package com.alibaba.chaosblade.exec.plugin.taf.model;

import com.alibaba.chaosblade.exec.common.model.matcher.BasePredicateMatcherSpec;
import com.alibaba.chaosblade.exec.plugin.taf.TafConstant;

/**
 * @author saikei
 * @email lishiji@huya.com
 */
public class ServantMatcherSpec extends BasePredicateMatcherSpec {

    @Override
    public String getName() {
        return TafConstant.SERVANT;
    }

    @Override
    public String getDesc() {
        return "to tag servant role experiment";
    }

    @Override
    public boolean noArgs() {
        return true;
    }

    @Override
    public boolean required() {
        return false;
    }
}
