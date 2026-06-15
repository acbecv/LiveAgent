package com.hmdp.limit.condition;


import com.hmdp.limit.constant.ConfigConstant;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * <p>
 * 限流切面开关
 * </p>
 * @author yang
 * @date 2023/8/9
 */
public class LimitAspectCondition implements Condition {
    @Override
    public boolean matches(ConditionContext conditionContext,  AnnotatedTypeMetadata annotatedTypeMetadata) {
        //检查配置文件是否包含limit.type属性
        return conditionContext.getEnvironment().containsProperty(ConfigConstant.LIMIT_TYPE);
    }
}