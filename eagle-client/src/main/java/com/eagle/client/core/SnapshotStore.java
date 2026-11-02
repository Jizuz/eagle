package com.eagle.client.core;

import com.eagle.common.dto.ConfigNodeDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地磁盘快照存储: {cacheDir}/{env}/{group}.json
 * <p>双重降级的最后一环 —— ZK完全不可用且服务重启时，仍可从磁盘快照恢复配置，
 * 保证本地应用可继续运行（文档 5.2）。</p>
 */
public class SnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStore.class);

    private final Path file;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SnapshotStore(String cacheDir, String env, String groupId) {
        this.file = Paths.get(cacheDir, env, groupId + ".json");
    }

    /**
     * 保存配置快照（临时文件+原子替换，避免写一半损坏）
     */
    public void save(Map<String, ConfigNodeDTO> configs) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, objectMapper.writeValueAsBytes(configs));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            // 快照写失败不影响主流程，仅告警
            log.warn("[Eagle] 本地快照保存失败: {} ({})", file, e.getMessage());
        }
    }

    /**
     * 加载配置快照（文件不存在或损坏时返回空Map，不抛异常）
     */
    public Map<String, ConfigNodeDTO> load() {
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }
        try {
            byte[] data = Files.readAllBytes(file);
            return objectMapper.readValue(new String(data, StandardCharsets.UTF_8),
                    new TypeReference<LinkedHashMap<String, ConfigNodeDTO>>() {
                    });
        } catch (Exception e) {
            log.warn("[Eagle] 本地快照加载失败(忽略): {} ({})", file, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
