package com.eagle.client.spring;

import com.eagle.client.core.DefaultEagleConfigClient;
import com.eagle.client.core.EagleClientHolder;
import com.eagle.client.core.EagleConfigClient;
import com.eagle.client.core.EagleConfigProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/**
 * Eagle客户端自动装配: 暴露EagleConfigClient Bean供业务注入使用。
 */
@AutoConfiguration
@ConditionalOnClass(EagleConfigClient.class)
public class EagleConfigAutoConfiguration {

    /**
     * 优先复用EnvironmentPostProcessor阶段已创建的实例，避免重复建连
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(EagleConfigClient.class)
    @Conditional(EagleClientAvailableCondition.class)
    public EagleConfigClient eagleConfigClient(Environment environment) {
        EagleConfigClient existing = EagleClientHolder.get();
        if (existing != null) {
            return existing;
        }
        // 兜底: 显式开启(eagle.config.enabled=true)但EPP未初始化的场景
        EagleConfigProperties props = Binder.get(environment)
                .bind("eagle.config", Bindable.of(EagleConfigProperties.class))
                .orElse(new EagleConfigProperties());
        DefaultEagleConfigClient client = new DefaultEagleConfigClient(props);
        client.init();
        EagleClientHolder.setClient(client, props.getPropertyPrefix());
        return client;
    }

    /**
     * spring-cloud-context在类路径上时，桥接配置变更到Spring刷新体系
     * （EnvironmentChangeEvent重绑定 + @RefreshScope Bean重建）
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.context.scope.refresh.RefreshScope")
    static class CloudRefreshConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EagleCloudConfigRefresher eagleCloudConfigRefresher(EagleConfigClient client,
                                                                   ConfigurableApplicationContext applicationContext) {
            return new EagleCloudConfigRefresher(client, applicationContext);
        }
    }
}
