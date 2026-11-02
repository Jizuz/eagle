package com.eagle.client.core;

import com.eagle.common.dto.ConfigChangeEvent;
import com.eagle.common.dto.ConfigNodeDTO;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Eagle配置中心客户端接口
 */
public interface EagleConfigClient extends AutoCloseable {

    /**
     * 获取配置并转换为指定类型
     *
     * @throws com.eagle.common.exception.EagleException 配置不存在、或值与目标类型不匹配时抛出
     */
    <T> T getConfig(String key, Class<T> type);

    /**
     * 获取配置并转换为指定类型，配置不存在或转换失败时返回默认值
     */
    <T> T getConfig(String key, Class<T> type, T defaultValue);

    /**
     * 获取原始配置节点（type + value），不存在返回null
     */
    ConfigNodeDTO getRawConfig(String key);

    /**
     * 获取当前全部配置快照（内存缓存）
     */
    Map<String, ConfigNodeDTO> getAllConfigs();

    /**
     * 是否处于降级模式（ZK断连，正使用本地缓存提供服务）
     */
    boolean isDegraded();

    /**
     * 注册配置变更监听器（回调在独立单线程中异步执行，禁止长时间阻塞）
     */
    void addChangeListener(Consumer<ConfigChangeEvent> listener);

    @Override
    void close();
}
