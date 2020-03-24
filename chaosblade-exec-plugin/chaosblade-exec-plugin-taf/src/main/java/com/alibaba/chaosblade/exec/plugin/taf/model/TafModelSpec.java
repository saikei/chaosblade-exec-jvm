package com.alibaba.chaosblade.exec.plugin.taf.model;


import com.alibaba.chaosblade.exec.common.aop.PredicateResult;
import com.alibaba.chaosblade.exec.common.exception.ExperimentException;
import com.alibaba.chaosblade.exec.common.model.FrameworkModelSpec;
import com.alibaba.chaosblade.exec.common.model.Model;
import com.alibaba.chaosblade.exec.common.model.handler.PreCreateInjectionModelHandler;
import com.alibaba.chaosblade.exec.common.model.handler.PreDestroyInjectionModelHandler;
import com.alibaba.chaosblade.exec.common.model.matcher.MatcherModel;
import com.alibaba.chaosblade.exec.common.model.matcher.MatcherSpec;
import com.alibaba.chaosblade.exec.plugin.taf.TafConstant;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * @author saikei
 * @email lishiji@huya.com
 */
public class TafModelSpec extends FrameworkModelSpec implements PreCreateInjectionModelHandler,
        PreDestroyInjectionModelHandler {

    @Override
    protected List<MatcherSpec> createNewMatcherSpecs() {
        ArrayList<MatcherSpec> matcherSpecs = new ArrayList<MatcherSpec>();
        matcherSpecs.add(new ClientMatcherSpec());
        matcherSpecs.add(new ServantMatcherSpec());
        matcherSpecs.add(new ServantNameMatcherSpec());
        matcherSpecs.add(new FunctionNameMatcherSpec());
        return matcherSpecs;
    }

    @Override
    public String getTarget() {
        return TafConstant.TARGET_NAME;
    }

    @Override
    public String getShortDesc() {
        return "taf experiment";
    }

    @Override
    public String getLongDesc() {
        return "Taf experiment for testing service delay and exception.";
    }

    @Override
    protected PredicateResult preMatcherPredicate(Model model) {
        if (model == null) {
            return PredicateResult.fail("matcher not found for taf");
        }
        MatcherModel matcher = model.getMatcher();
        Set<String> keySet = matcher.getMatchers().keySet();
        for (String key : keySet) {
            if (key.equals(TafConstant.CLIENT) || key.equals(TafConstant.SERVANT)) {
                return PredicateResult.success();
            }
        }
        return PredicateResult.fail("less necessary matcher is client or servant for taf");
    }

    @Override
    public String getExample() {
        return "taf delay --time 3000 --client --servantname app.server.obj --functionname hello";
    }

    @Override
    public void preCreate(String suid, Model model) throws ExperimentException {

    }

    @Override
    public void preDestroy(String suid, Model model) throws ExperimentException {

    }
}
