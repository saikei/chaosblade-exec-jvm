package com.alibaba.chaosblade.exec.plugin.taf.model;


import com.alibaba.chaosblade.exec.common.model.matcher.BasePredicateMatcherSpec;
import com.alibaba.chaosblade.exec.plugin.taf.TafConstant;

/**
 * @author saikei
 * @email lishiji@huya.com
 */
public class ServantNameMatcherSpec extends BasePredicateMatcherSpec {

    @Override
    public String getName() {
        return TafConstant.SERVANT_NAME;
    }

    @Override
    public String getDesc() {
        return "The name of servant";
    }

    @Override
    public boolean noArgs() {
        return false;
    }

    @Override
    public boolean required() {
        return false;
    }
}
