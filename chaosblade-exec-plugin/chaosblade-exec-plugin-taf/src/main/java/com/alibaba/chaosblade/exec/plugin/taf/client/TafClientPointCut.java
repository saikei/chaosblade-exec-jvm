package com.alibaba.chaosblade.exec.plugin.taf.client;

import com.alibaba.chaosblade.exec.common.aop.PointCut;
import com.alibaba.chaosblade.exec.common.aop.matcher.clazz.ClassMatcher;
import com.alibaba.chaosblade.exec.common.aop.matcher.clazz.NameClassMatcher;
import com.alibaba.chaosblade.exec.common.aop.matcher.clazz.OrClassMatcher;
import com.alibaba.chaosblade.exec.common.aop.matcher.method.*;

/**
 * @author saikei
 * @email lishiji@huya.com
 */
public class TafClientPointCut implements PointCut {

    @Override
    public ClassMatcher getClassMatcher() {
        OrClassMatcher classMatcher = new OrClassMatcher();
        classMatcher
                .or(new NameClassMatcher("com.huya.taf.client.rpc.taf.TafInvoker"))

                .or(new NameClassMatcher("com.qq.tars.client.rpc.tars.TarsInvoker"));
        return classMatcher;
    }

    @Override
    public MethodMatcher getMethodMatcher() {
        AndMethodMatcher methodMatcher = new AndMethodMatcher();
        ParameterMethodMatcher parameterMethodMatcher = new ParameterMethodMatcher(new String[]{
                "com.huya.taf.client.rpc.ServantInvokeContext"}, 1,
                ParameterMethodMatcher.EQUAL);
        methodMatcher.and(new NameMethodMatcher("doInvokeServant"));

        AndMethodMatcher methodMatcherQq = new AndMethodMatcher();
        ParameterMethodMatcher parameterMethodMatcherQq = new ParameterMethodMatcher(new String[]{
                "com.qq.tars.client.rpc.ServantInvokeContext"}, 1,
                ParameterMethodMatcher.EQUAL);

        return new OrMethodMatcher().or(methodMatcher).or(methodMatcherQq);
    }
}
