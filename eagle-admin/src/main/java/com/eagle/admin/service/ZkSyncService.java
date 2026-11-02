package com.eagle.admin.service;

import com.eagle.admin.config.EagleZkProperties;
import com.eagle.common.dto.ConfigNodeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * ZooKeeper写入服务: 将配置以标准JSON同步到 /config/{env}/{group}/{key}
 */
@Slf4j
@Service
public class ZkSyncService {

    private final CuratorFramework client;
    private final EagleZkProperties properties;
    private final ObjectMapper objectMapper;

    public ZkSyncService(CuratorFramework client, EagleZkProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 写入ZK节点（幂等）: 节点存在则更新，不存在则创建（父节点自动创建）
     */
    public void writeNode(String env, String groupId, String configKey, ConfigNodeDTO node) throws Exception {
        String path = buildPath(env, groupId, configKey);
        byte[] data = objectMapper.writeValueAsBytes(node);
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path, data);
            log.info("ZK节点已创建: {} -> {}", path, new String(data, StandardCharsets.UTF_8));
        } else {
            client.setData().forPath(path, data);
            log.info("ZK节点已更新: {} -> {}", path, new String(data, StandardCharsets.UTF_8));
        }
    }

    /**
     * 删除ZK节点（删除配置时调用），节点不存在时静默返回false
     */
    public boolean deleteNode(String env, String groupId, String configKey) throws Exception {
        String path = buildPath(env, groupId, configKey);
        if (client.checkExists().forPath(path) != null) {
            client.delete().guaranteed().forPath(path);
            log.info("ZK节点已删除: {}", path);
            return true;
        }
        return false;
    }

    /**
     * 组装节点路径: /config/{env}/{group}/{key}
     */
    public String buildPath(String env, String groupId, String configKey) {
        return properties.getRoot() + "/" + env + "/" + groupId + "/" + configKey;
    }
}
