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
import com.adobe.abp.regola.results.UnaryBooleanRuleResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;

/**
 * A rule that returns the inverse result of the subrule.
 * A NOT with no operand rule is an invalid definition (there is no sensible value to invert), so it
 * fails fast: {@code status()} completes exceptionally and {@code snapshot()} reports {@link Result#FAILED}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings("this-escape")
public class NotRule extends UnaryBooleanRule {

    public NotRule() {
        super(RuleType.NOT.getName());
    }

    public NotRule(Rule rule) {
        this();
        setRule(rule);
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new LockingEvaluationResult() {

            // The evaluated operand, published once in startEvaluation() before its callback is wired up.
            private volatile EvaluationResult notResult;

            @Override
            protected void startEvaluation() {
                final Rule rule = getRule();
                if (rule == null) {
                    // A missing operand is malformed configuration, not an empty-operand identity case.
                    completeExceptionally(new IllegalStateException("NOT rule requires a non-null operand rule"));
                    return;
                }

                final EvaluationResult evaluationResult = rule.evaluate(factsResolver);
                notResult = evaluationResult; // safe publication before the callback below can fire

                final boolean ignore = rule.isIgnore();
                evaluationResult.status()
                        .whenComplete((subresult, throwable) -> evaluateSubresult(subresult, throwable, ignore));
            }

            private void evaluateSubresult(Result subresult, Throwable throwable, boolean ignore) {
                if (!ignore && throwable != null) {
                    // completeExceptionally() publishes Result.FAILED before the future transitions,
                    // fixing the original ordering bug where a callback could observe MAYBE instead of FAILED.
                    completeExceptionally(throwable);
                    return;
                }
                decideUnderLock(() -> {
                    if (ignore) {
                        return Optional.of(Result.VALID); // an ignored subrule ⇒ VALID regardless of its result
                    } else if (subresult == Result.VALID) {
                        return Optional.of(Result.INVALID);
                    } else if (subresult == Result.INVALID) {
                        return Optional.of(Result.VALID);
                    }
                    // FAILED / OPERATION_NOT_SUPPORTED / MAYBE pass through unchanged
                    return Optional.of(subresult);
                });
            }

            @Override
            protected void afterCompletion(Result completedResult, Throwable throwable) {
                Optional.ofNullable(getAction())
                        .ifPresent(action -> action.onCompletion(completedResult, throwable, snapshot()));
            }

            @Override
            public RuleResult snapshot() {
                final EvaluationResult capturedNotResult = notResult;
                final Result capturedResult = getResult();
                return UnaryBooleanRuleResult.builder().with(r -> {
                    r.type = getType();
                    r.description = getDescription();
                    r.result = capturedResult;
                    r.rule = Optional.ofNullable(capturedNotResult)
                            .map(EvaluationResult::snapshot)
                            .orElse(null);
                    r.ignored = isIgnore();
                }).build();
            }
        };
    }

}
