package com.eagle.client.spring;

import com.eagle.client.core.EagleConfigClient;
import org.springframework.core.env.EnumerablePropertySource;

/**
 * Eagle动态PropertySource: 将配置中心接入Spring Environment。
 * <ul>
 *     <li>原生支持 @Value("${configKey}") / Environment.getProperty 读取</li>
 *     <li>getProperty实时读取客户端内存缓存，配置变更后新创建的Bean立即拿到新值</li>
 *     <li>存量Bean的@Value刷新需配合 @RefreshScope（spring-cloud-context，见EagleCloudConfigRefresher）</li>
 * </ul>
 */
public class EaglePropertySource extends EnumerablePropertySource<EagleConfigClient> {

    private final String prefix;

    public EaglePropertySource(String name, EagleConfigClient client, String propertyPrefix) {
        super(name, client);
        String p = propertyPrefix == null ? "" : propertyPrefix.trim();
        this.prefix = p.isEmpty() || p.endsWith(".") ? p : p + ".";
    }

    @Override
    public String[] getPropertyNames() {
        return source.getAllConfigs().keySet().stream()
                .map(this::toPropertyName)
                .toArray(String[]::new);
    }

    @Override
    public boolean containsProperty(String name) {
        return name != null && name.startsWith(prefix)
                && source.getRawConfig(toConfigKey(name)) != null;
    }

    @Override
    public Object getProperty(String name) {
        if (!containsProperty(name)) {
            return null;
        }
        return source.getConfig(toConfigKey(name), String.class, null);
    }

    private String toPropertyName(String configKey) {
        return prefix + configKey;
    }

    private String toConfigKey(String propertyName) {
        return prefix.isEmpty() ? propertyName : propertyName.substring(prefix.length());
    }
}
