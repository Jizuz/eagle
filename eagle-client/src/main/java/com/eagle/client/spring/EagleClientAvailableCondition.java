package com.eagle.client.spring;

import com.eagle.client.core.EagleClientHolder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 客户端Bean创建条件:
 * EnvironmentPostProcessor已创建客户端（holder就绪），或显式配置 eagle.config.enabled=true
 */
class EagleClientAvailableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (EagleClientHolder.isInitialized()) {
            return true;
        }
        return "true".equalsIgnoreCase(context.getEnvironment().getProperty("eagle.config.enabled"));
    }
}
