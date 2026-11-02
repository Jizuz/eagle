package com.eagle.admin.config;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Curator客户端配置: 启动时同步建连并确保根节点存在
 */
@Configuration
public class ZkClientConfig {

    @Bean(destroyMethod = "close")
    public CuratorFramework curatorFramework(EagleZkProperties properties) {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(properties.getAddress())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                // 禁用EnsembleTracker: 防止ZK集群广播的主机名地址(如docker容器名)覆盖配置的连接串,
                // 否则宿主机客户端会话失效后将无法解析主机名而永远无法重连
                .ensembleTracker(false)
                .connectionTimeoutMs(properties.getConnectTimeoutMs())
                .sessionTimeoutMs(properties.getSessionTimeoutMs());
        if (properties.isAuthEnable() && StringUtils.hasText(properties.getAuthPassword())) {
            // 同步服务账号具备读写权限（ACL需在ZK服务端配置）
            builder.authorization(properties.getAuthScheme(),
                    (properties.getAuthUser() + ":" + properties.getAuthPassword()).getBytes(StandardCharsets.UTF_8));
        }
        CuratorFramework client = builder.build();
        client.start();
        try {
            if (!client.blockUntilConnected(15, TimeUnit.SECONDS)) {
                client.close();
                throw new IllegalStateException("ZooKeeper连接超时: " + properties.getAddress());
            }
            // 确保配置根节点存在
            if (client.checkExists().forPath(properties.getRoot()) == null) {
                client.create().creatingParentsIfNeeded().forPath(properties.getRoot());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            client.close();
            throw new IllegalStateException("ZooKeeper连接等待被中断", e);
        } catch (Exception e) {
            client.close();
            throw new IllegalStateException("初始化ZooKeeper根节点失败: " + properties.getRoot(), e);
        }
        return client;
    }
}
