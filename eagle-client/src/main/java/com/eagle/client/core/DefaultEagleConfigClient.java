package com.eagle.client.core;

import com.eagle.common.dto.ConfigChangeEvent;
import com.eagle.common.dto.ConfigNodeDTO;
import com.eagle.common.enums.ChangeType;
import com.eagle.common.enums.ValueType;
import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Eagle配置中心客户端默认实现（文档 5.2）。
 * <p>可靠性设计:</p>
 * <ul>
 *     <li>内存缓存(ConcurrentHashMap) + 磁盘快照 双级本地缓存，ZK断连期间继续提供服务（降级）</li>
 *     <li>CuratorCache监听 /config/{env}/{group} 子树实现秒级变更感知（NodeCache/PathChildrenCache在Curator 5.x已废弃）</li>
 *     <li>ConnectionStateListener: 断连标记降级；重连后全量对账，保证不漏变更</li>
 *     <li>周期性全量对账（syncIntervalMs，默认30s），自愈watch丢失等极端边界场景</li>
 *     <li>变更回调在独立单线程中异步执行，业务监听器异常互不影响</li>
 * </ul>
 */
@Slf4j
public class DefaultEagleConfigClient implements EagleConfigClient {

    private final EagleConfigProperties properties;
    private final String basePath;
    private final CuratorFramework curator;
    private final CuratorCache cache;
    private final SnapshotStore snapshotStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, ConfigNodeDTO> localCache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ConfigChangeEvent>> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "eagle-config-notify");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "eagle-config-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final CountDownLatch initializedLatch = new CountDownLatch(1);

    private volatile boolean degraded = false;
    private volatile boolean closed = false;

    public DefaultEagleConfigClient(EagleConfigProperties properties) {
        this.properties = properties;
        this.basePath = properties.getRootPath() + "/" + properties.getEnv() + "/" + properties.getGroupId();
        this.snapshotStore = new SnapshotStore(properties.getCacheDir(), properties.getEnv(), properties.getGroupId());
        this.curator = CuratorFrameworkFactory.builder()
                .connectString(properties.getZkAddress())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                // 禁用EnsembleTracker: 防止ZK集群广播的主机名地址(如docker容器名)覆盖配置的连接串,
                // 否则宿主机客户端会话失效后将无法解析主机名而永远无法重连
                .ensembleTracker(false)
                .connectionTimeoutMs(properties.getConnectTimeoutMs())
                .sessionTimeoutMs(properties.getSessionTimeoutMs())
                .build();
        CuratorCacheListener cacheListener = CuratorCacheListener.builder()
                .afterInitialized()
                .forCreates(this::onNodeCreated)
                .forChanges(this::onNodeChanged)
                .forDeletes(this::onNodeDeleted)
                .forInitialized(this::onCacheInitialized)
                .build();
        this.cache = CuratorCache.build(curator, basePath);
        cache.listenable().addListener(cacheListener);
    }

    /**
     * 初始化: 先加载磁盘快照兜底 → 建立ZK连接与监听 → 拉取全量配置
     */
    public void init() {
        // 1. 先加载磁盘快照（ZK完全不可用且服务重启时的最终降级数据源）
        Map<String, ConfigNodeDTO> snapshot = snapshotStore.load();
        if (!snapshot.isEmpty()) {
            localCache.putAll(snapshot);
            log.info("[Eagle] 已加载本地磁盘快照: {}项 (env={}, group={})",
                    snapshot.size(), properties.getEnv(), properties.getGroupId());
        }
        // 2. 注册连接状态监听（重连对账/降级标记）
        curator.getConnectionStateListenable().addListener((client, state) -> onConnectionState(state));
        // 3. 启动ZK连接与缓存监听
        curator.start();
        boolean connected = false;
        try {
            connected = curator.blockUntilConnected(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        cache.start();
        if (connected) {
            try {
                // 等待初始数据加载完成（超时不阻塞启动，后台事件继续补齐）
                initializedLatch.await(Math.max(properties.getConnectTimeoutMs(), 5000L), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            degraded = true;
            log.warn("[Eagle] ZooKeeper暂不可达({}), 进入降级模式, 先使用本地缓存提供服务", properties.getZkAddress());
        }
        // 4. 周期性全量对账，自愈watch丢失等边界场景
        if (properties.getSyncIntervalMs() > 0) {
            syncExecutor.scheduleWithFixedDelay(this::safeResync,
                    properties.getSyncIntervalMs(), properties.getSyncIntervalMs(), TimeUnit.MILLISECONDS);
        }
        log.info("[Eagle] 客户端初始化完成: env={}, group={}, zk={}, path={}, 配置数={}, 降级={}",
                properties.getEnv(), properties.getGroupId(), properties.getZkAddress(),
                basePath, localCache.size(), degraded);
    }

    // ==================== 配置读取 ====================

    @Override
    public <T> T getConfig(String key, Class<T> type) {
        ConfigNodeDTO node = localCache.get(key);
        if (node == null) {
            throw new EagleException(ErrorCode.CONFIG_NOT_FOUND,
                    "配置不存在: " + key + " (env=" + properties.getEnv() + ", group=" + properties.getGroupId() + ")");
        }
        return TypeConverter.convert(key, node, type);
    }

    @Override
    public <T> T getConfig(String key, Class<T> type, T defaultValue) {
        ConfigNodeDTO node = localCache.get(key);
        if (node == null) {
            return defaultValue;
        }
        try {
            return TypeConverter.convert(key, node, type);
        } catch (Exception e) {
            log.warn("[Eagle] 配置[{}]转换为目标类型{}失败, 返回默认值: {}", key, type.getSimpleName(), e.getMessage());
            return defaultValue;
        }
    }

    @Override
    public ConfigNodeDTO getRawConfig(String key) {
        return localCache.get(key);
    }

    @Override
    public Map<String, ConfigNodeDTO> getAllConfigs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(localCache));
    }

    @Override
    public boolean isDegraded() {
        return degraded;
    }

    @Override
    public void addChangeListener(Consumer<ConfigChangeEvent> listener) {
        listeners.add(listener);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            cache.close();
        } catch (Exception e) {
            log.warn("[Eagle] 关闭CuratorCache异常: {}", e.getMessage());
        }
        try {
            curator.close();
        } catch (Exception e) {
            log.warn("[Eagle] 关闭CuratorFramework异常: {}", e.getMessage());
        }
        notifyExecutor.shutdownNow();
        syncExecutor.shutdownNow();
        EagleClientHolder.clearIfSame(this);
        log.info("[Eagle] 客户端已关闭 (env={}, group={})", properties.getEnv(), properties.getGroupId());
    }

    // ==================== 连接状态处理（降级/恢复） ====================

    private void onConnectionState(ConnectionState state) {
        if (closed) {
            return;
        }
        switch (state) {
            case CONNECTED, RECONNECTED -> {
                if (degraded) {
                    log.info("[Eagle] ZooKeeper连接已恢复({}), 退出降级模式", state);
                }
                degraded = false;
                if (state == ConnectionState.RECONNECTED) {
                    // 断连期间的变更靠watch可能丢失，重连后主动全量对账
                    resyncFromZk();
                }
            }
            case SUSPENDED, LOST, READ_ONLY -> {
                degraded = true;
                log.warn("[Eagle] ZooKeeper连接异常({}), 进入降级模式, 继续使用本地缓存提供服务", state);
            }
            default -> log.debug("[Eagle] ZooKeeper连接状态: {}", state);
        }
    }

    // ==================== CuratorCache事件处理 ====================

    private void onNodeCreated(ChildData childData) {
        try {
            if (isBasePath(childData.getPath())) {
                return;
            }
            String key = keyOf(childData.getPath());
            ConfigNodeDTO newNode = parseNode(childData.getData());
            ConfigNodeDTO old = localCache.put(key, newNode);
            log.info("[Eagle] 检测到新增配置: {} -> {}", key, newNode);
            fireChange(key, old, newNode, ChangeType.MODIFY);
            persistSnapshot();
        } catch (Exception e) {
            log.error("[Eagle] 处理节点新增事件异常: {}", childData.getPath(), e);
        }
    }

    private void onNodeChanged(ChildData oldData, ChildData newData) {
        try {
            if (isBasePath(newData.getPath())) {
                return;
            }
            String key = keyOf(newData.getPath());
            ConfigNodeDTO oldNode = oldData == null ? null : parseNode(oldData.getData());
            ConfigNodeDTO newNode = parseNode(newData.getData());
            if (Objects.equals(oldNode, newNode)) {
                return;
            }
            localCache.put(key, newNode);
            log.info("[Eagle] 检测到配置变更: {}: {} -> {}", key, oldNode, newNode);
            fireChange(key, oldNode, newNode, ChangeType.MODIFY);
            persistSnapshot();
        } catch (Exception e) {
            log.error("[Eagle] 处理节点变更事件异常: {}", newData.getPath(), e);
        }
    }

    private void onNodeDeleted(ChildData childData) {
        try {
            if (isBasePath(childData.getPath())) {
                return;
            }
            String key = keyOf(childData.getPath());
            ConfigNodeDTO old = localCache.remove(key);
            log.info("[Eagle] 检测到配置删除: {}", key);
            fireChange(key, old, null, ChangeType.DELETE);
            persistSnapshot();
        } catch (Exception e) {
            log.error("[Eagle] 处理节点删除事件异常: {}", childData.getPath(), e);
        }
    }

    private void onCacheInitialized() {
        try {
            loadFromZkDirectly();
            persistSnapshot();
            log.info("[Eagle] ZK初始配置加载完成: {}项 (path={})", localCache.size(), basePath);
        } catch (Exception e) {
            log.error("[Eagle] ZK初始配置加载失败", e);
        } finally {
            initializedLatch.countDown();
        }
    }

    // ==================== 全量对账 ====================

    private void safeResync() {
        if (closed || degraded) {
            return;
        }
        resyncFromZk();
    }

    /**
     * 从ZK全量拉取并与本地缓存对账，差异部分更新缓存并触发变更回调。
     * 串行投递到通知线程执行，避免与事件回调并发竞争。
     */
    private void resyncFromZk() {
        if (closed) {
            return;
        }
        notifyExecutor.submit(() -> {
            try {
                Map<String, ConfigNodeDTO> latest = new HashMap<>();
                if (curator.checkExists().forPath(basePath) != null) {
                    for (String key : curator.getChildren().forPath(basePath)) {
                        byte[] data = curator.getData().forPath(basePath + "/" + key);
                        if (data != null && data.length > 0) {
                            latest.put(key, parseNode(data));
                        }
                    }
                }
                // 处理删除
                for (String key : new HashSet<>(localCache.keySet())) {
                    if (!latest.containsKey(key)) {
                        ConfigNodeDTO old = localCache.remove(key);
                        if (old != null) {
                            log.info("[Eagle] 配置已删除(全量对账): {}", key);
                            fireChange(key, old, null, ChangeType.DELETE);
                        }
                    }
                }
                // 处理新增/变更
                latest.forEach((key, node) -> {
                    ConfigNodeDTO old = localCache.put(key, node);
                    if (!Objects.equals(old, node)) {
                        log.info("[Eagle] 配置已变更(全量对账): {}: {} -> {}", key, old, node);
                        fireChange(key, old, node, ChangeType.MODIFY);
                    }
                });
                persistSnapshot();
            } catch (Exception e) {
                log.warn("[Eagle] ZK全量对账失败(下次重试): {}", e.getMessage());
            }
        });
    }

    private void loadFromZkDirectly() throws Exception {
        if (curator.checkExists().forPath(basePath) == null) {
            return; // 该分组尚无任何已发布配置
        }
        for (String key : curator.getChildren().forPath(basePath)) {
            byte[] data = curator.getData().forPath(basePath + "/" + key);
            if (data != null && data.length > 0) {
                localCache.put(key, parseNode(data));
            }
        }
    }

    // ==================== 内部工具 ====================

    private boolean isBasePath(String path) {
        return basePath.equals(path);
    }

    private String keyOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private ConfigNodeDTO parseNode(byte[] data) {
        if (data == null || data.length == 0) {
            return new ConfigNodeDTO(ValueType.STRING.getCode(), "");
        }
        try {
            return objectMapper.readValue(data, ConfigNodeDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("ZK节点数据解析失败: " + new String(data, StandardCharsets.UTF_8), e);
        }
    }

    private void persistSnapshot() {
        snapshotStore.save(localCache);
    }

    private void fireChange(String key, ConfigNodeDTO oldNode, ConfigNodeDTO newNode, ChangeType changeType) {
        if (listeners.isEmpty()) {
            return;
        }
        ConfigChangeEvent event = new ConfigChangeEvent();
        event.setKey(key);
        event.setOldNode(oldNode);
        event.setNewNode(newNode);
        event.setChangeType(changeType);
        notifyExecutor.submit(() -> {
            for (Consumer<ConfigChangeEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    log.error("[Eagle] 配置变更回调执行异常: key=" + key, e);
                }
            }
        });
    }
}
