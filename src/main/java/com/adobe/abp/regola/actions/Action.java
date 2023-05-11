/*
 *  Copyright 2023 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */
package com.adobe.abp.regola.actions;

import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.utils.lambda.TriConsumer;

public class Action {

    private String description;
    private TriConsumer<Result, Throwable, RuleResult> onCompletion;

    public String getDescription() {
        return description;
    }

    public Action setDescription(String description) {
        this.description = description;
        return this;
    }

    public Action setOnCompletion(TriConsumer<Result, Throwable, RuleResult> onCompletion) {
        this.onCompletion = onCompletion;
        return this;
    }

    public void onCompletion(Result result, Throwable throwable, RuleResult ruleResult) {
        this.onCompletion.accept(result, throwable, ruleResult);
    }

    @Override
    public String toString() {
        return "Action{" +
                "description='" + description + '\'' +
                ", onCompletion=" + (onCompletion != null) +
                '}';
    }
}
