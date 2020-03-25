package com.alibaba.chaosblade.exec.plugin.taf.client;


import com.alibaba.chaosblade.exec.common.aop.EnhancerModel;
import com.alibaba.chaosblade.exec.common.model.action.delay.BaseTimeoutExecutor;
import com.alibaba.chaosblade.exec.common.model.action.delay.TimeoutExecutor;
import com.alibaba.chaosblade.exec.common.model.matcher.MatcherModel;
import com.alibaba.chaosblade.exec.common.util.ReflectUtil;
import com.alibaba.chaosblade.exec.plugin.taf.TafConstant;
import com.alibaba.chaosblade.exec.plugin.taf.TafEnhancer;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


/**
 * @author saikei
 * @email lishiji@huya.com
 */
public class TabClientEnhancer extends TafEnhancer {

    private static final String SERVANT_PROXY_CONFIG = "config";
    private static final String GET_SYNC_TIMEOUT = "getSyncTimeout";
    private static final String GET_ASYNC_TIMEOUT = "getAsyncTimeout";
    private static final String GET_INVOKER = "getInvoker";
    private static final String OBJ_NAME = "objName";
    private static final String GET_METHOD_NAME = "getMethodName";
    private static final String TAF_TIMEOUT_EXCEPTION = "com.huya.taf.rpc.exc.TimeoutException";
    private static final String TARS_TIMEOUT_EXCEPTION = "com.qq.tars.rpc.exc.TimeoutException";

    private static final Logger LOGGER =  LoggerFactory.getLogger(TabClientEnhancer.class);
    @Override
    public EnhancerModel doBeforeAdvice(ClassLoader classLoader, String className, Object object, Method method, Object[] methodArguments) throws Exception {
        Object servantInvokerContext = methodArguments[0];
        if(object == null || servantInvokerContext == null){
            LOGGER.warn("The necessary parameter is null");
            return null;
        }
        Object invoker = ReflectUtil.invokeMethod(servantInvokerContext, GET_INVOKER, new Object[0], false);
        String servantName = ReflectUtil.getSuperclassFieldValue(invoker, OBJ_NAME, false);
        String functionName = ReflectUtil.invokeMethod(servantInvokerContext, GET_METHOD_NAME, new Object[0], false);

        Object config = ReflectUtil.getSuperclassFieldValue(invoker, SERVANT_PROXY_CONFIG, false);
        int timeout = getTimeOut(functionName, config);

        MatcherModel matcherModel = new MatcherModel();
        matcherModel.add(TafConstant.SERVANT_NAME, servantName);
        matcherModel.add(TafConstant.FUNCTION_NAME, functionName);
        matcherModel.add(TafConstant.CLIENT, "true");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("taf matchers: {}", JSON.toJSONString(matcherModel));
        }

        EnhancerModel enhancerModel = new EnhancerModel(classLoader, matcherModel);
        enhancerModel.setTimeoutExecutor(createTimeoutExecutor(classLoader, timeout, className));

        return enhancerModel;
    }

    @Override
    protected TimeoutExecutor createTimeoutExecutor(ClassLoader classLoader, long timeout, final String className) {
        return new BaseTimeoutExecutor(classLoader, timeout) {
            @Override
            public Exception generateTimeoutException(ClassLoader classLoader) {
                Class timeoutExceptionClass;
                String exceptionClassName = TAF_TIMEOUT_EXCEPTION;
                if(isTars(className)){
                    exceptionClassName = TARS_TIMEOUT_EXCEPTION;
                }
                try{
                    timeoutExceptionClass = classLoader.loadClass(exceptionClassName);
                    Class[] paramTypes = {String.class};
                    Object[] params = {"chaosblade-mock-TimeoutException,timeout=" + timeoutInMillis};
                    Constructor con = timeoutExceptionClass.getConstructor(paramTypes);
                    return (Exception)con.newInstance(params);
                }catch (ClassNotFoundException e) {

                    LOGGER.error("chaosblade-taf", "Can not find " + exceptionClassName, e);
                } catch (Exception e) {
                    LOGGER.error("chaosblade-taf", "Can not generate " + exceptionClassName, e);
                }
                return new RuntimeException(TafConstant.TIMEOUT_EXCEPTION_MSG);

            }
        };
    }

    private static boolean isAsync(String methodName) {
        return methodName != null && methodName.startsWith("async_");
    }

    private int getTimeOut(String methodName, Object servantProxyConfig) throws Exception {
        boolean isAsync = isAsync(methodName);
        if(isAsync){
            return ReflectUtil.invokeMethod(servantProxyConfig, GET_ASYNC_TIMEOUT, new Object[0], false);
        }
        return ReflectUtil.invokeMethod(servantProxyConfig, GET_SYNC_TIMEOUT, new Object[0], false);
    }
}
