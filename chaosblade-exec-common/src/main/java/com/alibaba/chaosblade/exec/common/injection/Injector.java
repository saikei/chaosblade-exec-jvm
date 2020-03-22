/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.chaosblade.exec.common.injection;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import com.alibaba.chaosblade.exec.common.aop.EnhancerModel;
import com.alibaba.chaosblade.exec.common.center.ManagerFactory;
import com.alibaba.chaosblade.exec.common.center.StatusMetric;
import com.alibaba.chaosblade.exec.common.constant.ModelConstant;
import com.alibaba.chaosblade.exec.common.exception.InterruptProcessException;
import com.alibaba.chaosblade.exec.common.model.Model;
import com.alibaba.chaosblade.exec.common.model.ModelSpec;
import com.alibaba.chaosblade.exec.common.model.action.ActionSpec;
import com.alibaba.chaosblade.exec.common.model.action.returnv.UnsupportedReturnTypeException;
import com.alibaba.chaosblade.exec.common.model.matcher.MatcherModel;
import com.alibaba.chaosblade.exec.common.util.StringUtil;
import com.alibaba.fastjson.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Changjun Xiao
 */
public class Injector {
    private static final Logger LOGGER = LoggerFactory.getLogger(Injector.class);

    /**
     * Inject
     * 关键注入
     *
     * @param enhancerModel
     * @throws InterruptProcessException
     */
    public static void inject(EnhancerModel enhancerModel) throws InterruptProcessException {
        String target = enhancerModel.getTarget();
        //列出实验 by 实验目标
        List<StatusMetric> statusMetrics = ManagerFactory.getStatusManager().getExpByTarget(
            target);
        //遍历实验
        for (StatusMetric statusMetric : statusMetrics) {
            Model model = statusMetric.getModel();
            //比对
            if (!compare(model, enhancerModel)) {
                continue;
            }
            try {
                boolean pass = limitAndIncrease(statusMetric);
                //超过实验次数或者比重
                if (!pass) {
                    LOGGER.info("Limited by: {}", JSON.toJSONString(model));
                    break;
                }
                LOGGER.info("Match rule: {}", JSON.toJSONString(model));
                //注入实验模型 action
                enhancerModel.merge(model);
                //model描述
                ModelSpec modelSpec = ManagerFactory.getModelSpecManager().getModelSpec(target);
                //action描述
                ActionSpec actionSpec = modelSpec.getActionSpec(model.getActionName());
                //注入实验，执行器run
                actionSpec.getActionExecutor().run(enhancerModel);
            } catch (InterruptProcessException e) {
                throw e;
            } catch (UnsupportedReturnTypeException e) {
                LOGGER.warn("unsupported return type for return experiment", e);
                // decrease the count if throw unexpected exception
                statusMetric.decrease();
            } catch (Throwable e) {
                LOGGER.warn("inject exception", e);
                // decrease the count if throw unexpected exception
                statusMetric.decrease();
            }
            // break it if compared success
            break;
        }
    }

    /**
     * @param statusMetric
     * @return
     */
    private static boolean limitAndIncrease(StatusMetric statusMetric) {
        Model model = statusMetric.getModel();
        //注入的有效次数
        String limitCount = model.getMatcher().get(ModelConstant.EFFECT_COUNT_MATCHER_NAME);
        if (!StringUtil.isBlank(limitCount)) {
            Long count = Long.valueOf(limitCount);
            if (statusMetric.getCount() >= count) {
                return false;
            }
            return statusMetric.increaseWithLock(count);
        }
        //限制得
        String limitPercent = model.getMatcher().get(ModelConstant.EFFECT_PERCENT_MATCHER_NAME);
        if (!StringUtil.isBlank(limitPercent)) {
            Integer percent = Integer.valueOf(limitPercent);
            Random random = new Random();
            int randomValue = random.nextInt(100) + 1;
            if (randomValue > percent) {
                return false;
            }
        }
        statusMetric.increase();
        return true;
    }

    /**
     * Compare the experiment rule with the date collected by method enhancer
     *
     * @param model
     * @param enhancerModel
     * @return
     */
    private static boolean compare(Model model, EnhancerModel enhancerModel) {
        MatcherModel matcher = model.getMatcher();
        if (matcher == null) {
            return true;
        }
        MatcherModel enhancerMatcherModel = enhancerModel.getMatcherModel();
        if (enhancerMatcherModel == null) {
            return false;
        }
        Map<String, String> matchers = matcher.getMatchers();
        for (Entry<String, String> entry : matchers.entrySet()) {
            // filter effect count and effect percent
            if (entry.getKey().equalsIgnoreCase(ModelConstant.EFFECT_COUNT_MATCHER_NAME) ||
                entry.getKey().equalsIgnoreCase(ModelConstant.EFFECT_PERCENT_MATCHER_NAME)) {
                continue;
            }
            String value = enhancerMatcherModel.get(entry.getKey());
            if (value == null) {
                return false;
            }
            if (!value.equalsIgnoreCase(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
