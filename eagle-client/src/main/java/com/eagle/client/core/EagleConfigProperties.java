package com.eagle.client.core;

import lombok.Data;

/**
 * Eagle客户端配置项
 */
@Data
public class EagleConfigProperties {

    /** 是否启用（Spring环境接入时生效） */
    private boolean enabled = true;

    /** ZK集群地址，逗号分隔，如 127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183 */
    private String zkAddress;

    /** 配置根节点 */
    private String rootPath = "/config";

    /** 环境: dev / test / prod */
    private String env;

    /** 服务分组 */
    private String groupId;

    /** 本地磁盘快照缓存目录 */
    private String cacheDir = System.getProperty("user.home") + "/.eagle/cache";

    /** 连接超时(ms) */
    private int connectTimeoutMs = 8000;

    /** 会话超时(ms) */
    private int sessionTimeoutMs = 60000;

    /** PropertySource是否置于最高优先级（配置中心覆盖本地application.yml） */
    private boolean propertyFirst = true;

    /** 注入Spring环境的属性名前缀，如 "eagle."；空串表示裸key直读 */
    private String propertyPrefix = "";

    /** 周期性全量对账间隔(ms)，用于自愈watch丢失等边界场景；&lt;=0表示关闭 */
    private long syncIntervalMs = 30000;
}
