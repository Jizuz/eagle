package com.eagle.client.core;

/**
 * 客户端全局持有器。
 * <p>EnvironmentPostProcessor阶段创建的客户端实例通过此持有器
 * 暴露给后续的自动装配阶段复用（避免重复建连）。</p>
 */
public final class EagleClientHolder {

    private static volatile EagleConfigClient client;
    private static volatile String propertyPrefix = "";

    private EagleClientHolder() {
    }

    public static void setClient(EagleConfigClient client, String propertyPrefix) {
        EagleClientHolder.client = client;
        EagleClientHolder.propertyPrefix = propertyPrefix == null ? "" : propertyPrefix;
    }

    public static EagleConfigClient get() {
        return client;
    }

    public static String getPropertyPrefix() {
        return propertyPrefix;
    }

    public static boolean isInitialized() {
        return client != null;
    }

    /**
     * 仅当持有的是同一实例时清除，避免误清后续新实例
     */
    public static void clearIfSame(EagleConfigClient client) {
        if (EagleClientHolder.client == client) {
            EagleClientHolder.client = null;
            EagleClientHolder.propertyPrefix = "";
        }
    }
}
