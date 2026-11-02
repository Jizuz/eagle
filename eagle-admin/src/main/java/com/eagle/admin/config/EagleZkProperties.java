package com.eagle.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ZooKeeper连接配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "eagle.zk")
public class EagleZkProperties {

    /** ZK集群地址，多个逗号分隔 */
    private String address = "127.0.0.1:2181";

    /** 配置根节点 */
    private String root = "/config";

    /** 是否启用digest ACL鉴权（ACL在ZK服务端配置） */
    private boolean authEnable = false;

    private String authScheme = "digest";

    private String authUser = "admin";

    private String authPassword = "";

    /** 连接超时(ms) */
    private int connectTimeoutMs = 5000;

    /** 会话超时(ms) */
    private int sessionTimeoutMs = 60000;
}
