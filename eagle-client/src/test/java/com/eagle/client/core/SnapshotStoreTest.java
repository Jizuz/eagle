package com.eagle.client.core;

import com.eagle.common.dto.ConfigNodeDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 磁盘快照存储单元测试（含中文值往返）
 */
class SnapshotStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrip() {
        SnapshotStore store = new SnapshotStore(tempDir.toString(), "dev", "order-service");
        Map<String, ConfigNodeDTO> data = new LinkedHashMap<>();
        data.put("switch", new ConfigNodeDTO("boolean", "true"));
        data.put("serviceName", new ConfigNodeDTO("string", "支付服务"));
        data.put("timeout", new ConfigNodeDTO("number", "5000"));

        store.save(data);

        Map<String, ConfigNodeDTO> loaded = store.load();
        assertEquals(3, loaded.size());
        assertEquals(new ConfigNodeDTO("boolean", "true"), loaded.get("switch"));
        assertEquals(new ConfigNodeDTO("string", "支付服务"), loaded.get("serviceName"));
        assertEquals(new ConfigNodeDTO("number", "5000"), loaded.get("timeout"));
        // 快照文件位于 {cacheDir}/{env}/{group}.json
        assertTrue(Files.exists(tempDir.resolve("dev").resolve("order-service.json")));
    }

    @Test
    void loadEmptyWhenFileAbsent() {
        SnapshotStore store = new SnapshotStore(tempDir.toString(), "test", "empty-group");
        assertTrue(store.load().isEmpty());
    }

    @Test
    void loadEmptyWhenFileCorrupted() {
        SnapshotStore store = new SnapshotStore(tempDir.toString(), "dev", "broken");
        store.save(Map.of("k", new ConfigNodeDTO("string", "v")));
        // 手动写坏快照文件
        try {
            Files.write(tempDir.resolve("dev").resolve("broken.json"), "not-a-json{{{".getBytes());
        } catch (Exception ignored) {
            // 测试环境异常忽略
        }
        assertTrue(store.load().isEmpty());
        assertFalse(store.load().containsKey("k"));
    }
}
