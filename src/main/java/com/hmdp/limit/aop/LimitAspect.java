package com.hmdp.limit.aop;


import com.hmdp.limit.condition.LimitAspectCondition;
import com.hmdp.limit.exception.LimitException;
import com.hmdp.limit.manager.Limiter;
import com.hmdp.limit.manager.LimiterManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.lang.reflect.Method;

/**
 * <p>
 * 限流切面，使用CGLIB代理
 * </p>
 * @author yang
 * @date 2023/8/9
 */
@Aspect
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Conditional(LimitAspectCondition.class)
public class LimitAspect {


    private final LimiterManager limiterManager;

    public LimitAspect(LimiterManager limiterManager) {
        this.limiterManager = limiterManager;
    }

    @Pointcut("@annotation(com.hmdp.limit.aop.Limit)")
    private void check() {

    }

    @Around("check()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        Method method = signature.getMethod();

        Limit limit = method.getAnnotation(Limit.class);

        if (limit != null) {

            Limiter limiter = Limiter.builder(limit);

            if (limiterManager.tryAccess(limiter)) {

                return joinPoint.proceed();

            } else {
                throw new LimitException(limiter.getMsg());
            }
        }
        // todo

        return joinPoint.proceed();

    }

}
