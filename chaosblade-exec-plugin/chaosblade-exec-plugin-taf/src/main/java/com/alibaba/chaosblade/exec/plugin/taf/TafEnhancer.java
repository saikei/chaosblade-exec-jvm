package com.alibaba.chaosblade.exec.plugin.taf;

import com.alibaba.chaosblade.exec.common.aop.BeforeEnhancer;
import com.alibaba.chaosblade.exec.common.aop.EnhancerModel;
import com.alibaba.chaosblade.exec.common.model.action.delay.TimeoutExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * @author saikei
 * @email lishiji@huya.com
 */
public abstract class TafEnhancer extends BeforeEnhancer {

    /**
     * doBeforeAdvice
     * @param classLoader
     * @param className
     * @param object
     * @param method
     * @param methodArguments
     * @return
     * @throws Exception
     */
    @Override
    public abstract EnhancerModel doBeforeAdvice(ClassLoader classLoader, String className, Object object,
                                        Method method, Object[] methodArguments) throws Exception;


    /**
     * Create timeout executor
     *
     * @param classLoader
     * @param timeout
     * @param className
     * @return
     */
    protected abstract TimeoutExecutor createTimeoutExecutor(ClassLoader classLoader, long timeout,
                                                             String className);

    /**
     * The version is tars(tencent)
     *
     * @param className
     * @return
     */
    protected boolean isTars(String className) {
        return className.startsWith("com.qq");
    }
}
