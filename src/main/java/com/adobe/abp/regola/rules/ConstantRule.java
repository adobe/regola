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

package com.adobe.abp.regola.rules;

import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A rule that always returns the same result, irrespective of the fact.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConstantRule extends Rule {

    private Result result;

    public ConstantRule() {
        this(Result.VALID);
    }

    public ConstantRule(Result value) {
        super(RuleType.CONSTANT.getName());
        this.result = value;
    }

    public Result getResult() {
        return result;
    }

    public ConstantRule setResult(Result result) {
        this.result = result;
        return this;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver facts) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE; // Initial value is always MAYBE

            @Override
            public RuleResult snapshot() {
                return RuleResult.baseBuilder().with(r -> {
                            r.type = getType();
                            r.result = result;
                            r.description = getDescription();
                        })
                        .build();
            }

            @Override
            public CompletableFuture<Result> status() {
                result = ConstantRule.this.result;
                return CompletableFuture.completedFuture(result)
                        .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                .ifPresent(action -> action.onCompletion(result, throwable, snapshot())));
            }
        };
    }
}
