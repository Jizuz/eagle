package com.eagle.client.spring;

import com.eagle.client.core.EagleClientHolder;
import com.eagle.client.core.EagleConfigClient;
import com.eagle.common.dto.ConfigChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collections;

/**
 * spring-cloud-context 存在时的配置刷新桥接:
 * <ol>
 *     <li>发布EnvironmentChangeEvent → 联动 @ConfigurationProperties 重新绑定</li>
 *     <li>RefreshScope.refreshAll() → 联动 @RefreshScope Bean重建（@Value 重新注入，文档 5.2）</li>
 * </ol>
 */
@Slf4j
public class EagleCloudConfigRefresher {

    private final EagleConfigClient client;
    private final ConfigurableApplicationContext applicationContext;

    public EagleCloudConfigRefresher(EagleConfigClient client, ConfigurableApplicationContext applicationContext) {
        this.client = client;
        this.applicationContext = applicationContext;
        client.addChangeListener(this::onConfigChange);
    }

    private void onConfigChange(ConfigChangeEvent event) {
        String propertyName = EagleClientHolder.getPropertyPrefix() + event.getKey();
        log.info("[Eagle] 配置变更触发Spring刷新: {} {} : {} -> {}",
                event.getChangeType(), propertyName, event.getOldNode(), event.getNewNode());
        try {
            // 1. 通知 @ConfigurationProperties 重绑定
            applicationContext.publishEvent(
                    new EnvironmentChangeEvent(Collections.singleton(propertyName)));
            // 2. 刷新 @RefreshScope Bean（@Value 重新注入）
            if (applicationContext.containsBean("refreshScope")) {
                RefreshScope refreshScope = applicationContext.getBean("refreshScope", RefreshScope.class);
                refreshScope.refreshAll();
            }
        } catch (Exception e) {
            log.error("[Eagle] Spring配置刷新失败: " + event.getKey(), e);
        }
    }
}
