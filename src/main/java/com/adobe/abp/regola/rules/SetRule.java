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
import com.adobe.abp.regola.results.ValuesRuleResult;
import com.adobe.abp.regola.utils.futures.FutureUtils;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.commons.collections4.CollectionUtils;

// TODO - add tests for integer, doubles, dates types
/**
 * This rule checks that a fact is contained in a set of values.
 * <p>
 * The rule supports the following operators:
 * <ul>
 * <li>{@link Operator#IN}</li>
 * <li>{@link Operator#INTERSECTS}</li>
 * </ul>
 * <p>
 * The rule will return {@link Result#OPERATION_NOT_SUPPORTED} for any other
 * combination of types or sets.
 *
 * @param <T> type of the fact
 */
public class SetRule<T> extends OperatorBasedRule {

    private Set<T> values;
    private final Executor executor;

    public SetRule() {
        this(null);
    }

    public SetRule(Executor executor) {
        super(RuleType.SET.getName());
        this.executor = executor;
    }

    public Set<T> getValues() {
        return values;
    }

    public SetRule<T> setValues(Set<T> values) {
        this.values = values;
        return this;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE;
            private T evaluatedFact;
            private Collection<T> evaluatedFacts;
            private String message;
            private Throwable cause;

            @Override
            public RuleResult snapshot() {
                return ValuesRuleResult.<T>builder().with(r -> {
                    r.type = getType();
                    r.key = getKey();
                    r.operator = getOperator();
                    r.description = getDescription();
                    r.result = result;
                    r.expectedValues = getValues();
                    r.actualValue = evaluatedFact;
                    r.actualValues = evaluatedFacts;
                    r.message = message;
                    r.cause = cause;
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                if (getOperator() == Operator.IN) {
                    return resolveFact(factsResolver, getKey())
                            .thenCompose(fact -> FutureUtils.supplyAsync(() -> handleSuccess(f -> checkIn(f), fact), executor))
                            .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                    .ifPresent(action -> action.onCompletion(result, throwable, snapshot())))
                            .exceptionally(this::handleFailure);
                }

                if (getOperator() == Operator.INTERSECTS) {
                    return resolveFact(factsResolver, getKey())
                            .thenCompose(fact -> FutureUtils.supplyAsync(() -> handleSuccess(f -> checkIntersects(f), fact), executor))
                            .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                    .ifPresent(action -> action.onCompletion(result, throwable, snapshot())))
                            .exceptionally(this::handleFailure);
                }

                return FutureUtils.supplyAsync(() -> {
                            result = Result.OPERATION_NOT_SUPPORTED;
                            return result;
                        }, executor)
                        .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                .ifPresent(action -> action.onCompletion(result, throwable, snapshot())));
            }

            private Result handleSuccess(Function<T, Result> checkFunction, T fact) {
                result = checkFunction.apply(fact);
                if (fact instanceof Collection) {
                    evaluatedFacts = (Collection<T>) fact;
                } else {
                    evaluatedFact = fact;
                }
                return result;
            }

            private Result handleFailure(Throwable throwable) {
                result = Result.FAILED;
                message = throwable.getMessage();
                cause = throwable;
                return result;
            }
        };
    }

    private Result checkIn(T fact) {
        return check(fact, (v, f) -> CollectionUtils.containsAll(getValues(), f));
    }

    private Result checkIntersects(T fact) {
        return check(fact, (v, f) -> CollectionUtils.containsAny(getValues(), f));
    }

    private Result check(T fact, BiFunction<Set<T>, Collection<?>, Boolean> checkCollection) {
        if (fact == null) {
            return Result.INVALID;
        }

        if (!getValues().isEmpty() && fact instanceof Collection && ((Collection<?>) fact).isEmpty()) {
            return Result.INVALID;
        }

        return Optional.of(fact)
                .filter(f -> {
                    if (f instanceof Collection) {
                        return checkCollection.apply(getValues(), (Collection<?>) f);
                    } else {
                        return getValues().contains(f);
                    }
                })
                .map(f -> Result.VALID)
                .orElse(Result.INVALID);
    }

    private CompletableFuture<T> resolveFact(FactsResolver factsResolver, String key) {
        return factsResolver.resolveFact(key)
                .thenApply(f -> {
                            if (f == null) {
                                return null;
                            }
                            try {
                                return (T) f;
                            } catch (ClassCastException e) {
                                throw new IllegalArgumentException("Fact must match type of the rule's values", e);
                            }
                        }
                );
    }

    @Override
    public String toString() {
        return "SingleValueRule{" +
                "type='" + getType() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", key='" + getKey() + '\'' +
                ", operator=" + getOperator() +
                ", values=" + values +
                ", ignore=" + isIgnore() +
                "}";
    }
}
