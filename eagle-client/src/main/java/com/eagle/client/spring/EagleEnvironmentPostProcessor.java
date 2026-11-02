package com.eagle.client.spring;

import com.eagle.client.core.DefaultEagleConfigClient;
import com.eagle.client.core.EagleClientHolder;
import com.eagle.client.core.EagleConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StringUtils;

/**
 * 环境后置处理器: 在Spring容器刷新前完成配置加载与PropertySource注入。
 * <p>必须晚于配置文件加载（ConfigDataEnvironmentPostProcessor，order = HIGHEST_PRECEDENCE + 10）
 * 执行，才能读到application.yml中的eagle.config.*配置。</p>
 */
public class EagleEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(EagleEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            EagleConfigProperties props = Binder.get(environment)
                    .bind("eagle.config", Bindable.of(EagleConfigProperties.class))
                    .orElse(null);
            if (props == null || !props.isEnabled()) {
                return;
            }
            if (!StringUtils.hasText(props.getZkAddress()) || !StringUtils.hasText(props.getEnv())
                    || !StringUtils.hasText(props.getGroupId())) {
                log.warn("[Eagle] eagle.config缺少必要配置(zk-address/env/group-id), 跳过配置中心接入");
                return;
            }
            // devtools重启等场景复用已创建的客户端
            if (EagleClientHolder.isInitialized()) {
                return;
            }
            DefaultEagleConfigClient client = new DefaultEagleConfigClient(props);
            client.init();
            EagleClientHolder.setClient(client, props.getPropertyPrefix());

            EaglePropertySource propertySource =
                    new EaglePropertySource("eagleConfig", client, props.getPropertyPrefix());
            MutablePropertySources sources = environment.getPropertySources();
            if (props.isPropertyFirst()) {
                sources.addFirst(propertySource);
            } else {
                sources.addLast(propertySource);
            }
            log.info("[Eagle] 配置中心PropertySource已接入: {} (优先级: {})",
                    propertySource.getName(), props.isPropertyFirst() ? "最高" : "最低");
        } catch (Exception e) {
            // 初始化失败不阻塞应用启动（降级为无配置中心模式）
            log.error("[Eagle] 配置中心初始化异常, 应用以降级模式继续启动", e);
        }
    }

    @Override
    public int getOrder() {
        // 晚于ConfigDataEnvironmentPostProcessor(HIGHEST_PRECEDENCE + 10)，确保能读到application.yml
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }
}
