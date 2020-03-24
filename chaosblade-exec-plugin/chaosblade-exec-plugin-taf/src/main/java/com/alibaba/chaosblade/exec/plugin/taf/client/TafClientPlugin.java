package com.alibaba.chaosblade.exec.plugin.taf.client;

import com.alibaba.chaosblade.exec.common.aop.Enhancer;
import com.alibaba.chaosblade.exec.common.aop.PointCut;
import com.alibaba.chaosblade.exec.plugin.taf.TafPlugin;

/**
 * @author saikei
 * @email lishiji@huya.com
 */
public class TafClientPlugin  extends TafPlugin {
    @Override
    public String getName() {
        return "client";
    }

    @Override
    public PointCut getPointCut() {
        return new TafClientPointCut();
    }

    @Override
    public Enhancer getEnhancer() {
        return new TabClientEnhancer();
    }
}
