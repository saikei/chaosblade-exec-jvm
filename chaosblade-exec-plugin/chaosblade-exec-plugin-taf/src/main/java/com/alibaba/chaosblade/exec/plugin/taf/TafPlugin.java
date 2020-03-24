package com.alibaba.chaosblade.exec.plugin.taf;

import com.alibaba.chaosblade.exec.common.aop.Plugin;
import com.alibaba.chaosblade.exec.common.model.ModelSpec;
import com.alibaba.chaosblade.exec.plugin.taf.model.TafModelSpec;

/**
 * @author saikei
 * @email lishiji@huya.com
 */
public abstract class TafPlugin implements Plugin {
    @Override
    public ModelSpec getModelSpec() {
        return new TafModelSpec();
    }
}
