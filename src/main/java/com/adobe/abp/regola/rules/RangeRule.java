/*
 *  Copyright 2025 Adobe. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import jdk.jfr.Experimental;

/**
 * Represents a rule for evaluating facts against a range of values.
 * <p>
 * This rule is experimental and subject to change. It supports basic range operations
 * for types that implement {@link Comparable}.
 * </p>
 * <p>
 * The following operators are supported:
 * </p>
 * <ul>
 *     <li>{@link Operator#BETWEEN} - Checks if a value lies within a specified range.</li>
 *     <li>{@link Operator#IS_BEFORE} - Checks if a value is less than a specified minimum.</li>
 *     <li>{@link Operator#IS_AFTER} - Checks if a value is greater than a specified maximum.</li>
 * </ul>
 * <p>
 * This rule can be configured with minimum and maximum values, as well as whether the
 * range boundaries are inclusive or exclusive.
 * </p>
 *
 * @param <T> the type of the fact being evaluated (must extend {@link Comparable})
 */
@Experimental
public class RangeRule<T extends Comparable<T>> extends OperatorBasedRule {

    private final List<Operator> SUPPORTED_OPERATORS = List.of(Operator.BETWEEN, Operator.IS_BEFORE, Operator.IS_AFTER);

    private final Executor executor;
    private T min;
    private T max;
    private boolean minExclusive;
    private boolean maxExclusive;

    public RangeRule() {
        this(null);
    }

    public RangeRule(Executor executor) {
        super(RuleType.RANGE.getName());
        this.executor = executor;
    }

    public T getMin() {
        return min;
    }

    public RangeRule<T> setMin(T min) {
        this.min = min;
        return this;
    }

    public T getMax() {
        return max;
    }

    public RangeRule<T> setMax(T max) {
        this.max = max;
        return this;
    }

    public boolean isMinExclusive() {
        return minExclusive;
    }

    public RangeRule<T> setMinExclusive(boolean minExclusive) {
        this.minExclusive = minExclusive;
        return this;
    }

    public boolean isMaxExclusive() {
        return maxExclusive;
    }

    public RangeRule<T> setMaxExclusive(boolean maxExclusive) {
        this.maxExclusive = maxExclusive;
        return this;
    }

    private Collection<T> generateExpectedValues() {
        var values = new ArrayList<T>();
        switch (getOperator()) {
            case BETWEEN:
                values.add(min);
                values.add(max);
                break;
            case IS_BEFORE:
                values.add(min);
                break;
            case IS_AFTER:
                values.add(max);
                break;
        }

        values.removeIf(Objects::isNull);
        return values;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE;
            private T evaluatedFact;
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
                    r.actualValue = evaluatedFact;
                    r.expectedValues = generateExpectedValues();
                    r.message = message;
                    r.cause = cause;
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
                if (!SUPPORTED_OPERATORS.contains(getOperator())) {
                    return FutureUtils.supplyAsync(() -> {
                                result = Result.OPERATION_NOT_SUPPORTED;
                                return result;
                            }, executor)
                            .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                    .ifPresent(action -> action.onCompletion(result, throwable, snapshot())));
                }

                return resolveFact(factsResolver, getKey())
                        .thenCompose(fact -> FutureUtils.supplyAsync(() -> handleSuccess(fact), executor))
                        .whenComplete((result, throwable) -> Optional.ofNullable(getAction())
                                .ifPresent(action -> action.onCompletion(result, throwable, snapshot())))
                        .exceptionally(this::handleFailure);
            }

            private Result handleSuccess(T fact) {
                if (fact == null) {
                    result = Result.INVALID;
                    return result;
                }

                evaluatedFact = fact;
                result = evaluateRange(fact);
                return result;
            }

            private Result evaluateRange(T fact) {
                var operator = getOperator();

                if (operator == Operator.BETWEEN) {
                    if (min == null || max == null) {
                        message = "min or max are null";
                        return Result.FAILED;
                    }
                    return evaluateBetween(fact);
                }

                if (operator == Operator.IS_BEFORE) {
                    if (min == null) {
                        message = "min is null";
                        return Result.FAILED;
                    }
                    return evaluateIsBefore(fact);
                }

                if (operator == Operator.IS_AFTER) {
                    if (max == null) {
                        message = "max is null";
                        return Result.FAILED;
                    }
                    return evaluateIsAfter(fact);
                }

                return Result.OPERATION_NOT_SUPPORTED;
            }

            private Result handleFailure(Throwable throwable) {
                result = Result.FAILED;
                message = throwable.getMessage();
                cause = throwable;
                return result;
            }
        };
    }

    private Result evaluateBetween(T fact) {
        int minComparison = fact.compareTo(min);
        int maxComparison = fact.compareTo(max);

        boolean afterMin = minExclusive ? minComparison > 0 : minComparison >= 0;
        boolean beforeMax = maxExclusive ? maxComparison < 0 : maxComparison <= 0;

        return afterMin && beforeMax ? Result.VALID : Result.INVALID;
    }

    private Result evaluateIsBefore(T fact) {
        int minComparison = fact.compareTo(min);

        boolean isBefore = minExclusive ? minComparison <= 0 : minComparison < 0;

        return isBefore ? Result.VALID : Result.INVALID;
    }

    private Result evaluateIsAfter(T fact) {
        int maxComparison = fact.compareTo(max);

        boolean isAfter = maxExclusive ? maxComparison >= 0 : maxComparison > 0;

        return isAfter ? Result.VALID : Result.INVALID;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<T> resolveFact(FactsResolver factsResolver, String key) {
        return factsResolver.resolveFact(key)
                .thenApply(f -> {
                    if (f == null) {
                        return null;
                    }

                    try {
                        return (T) f;
                    } catch (ClassCastException e) {
                        throw new IllegalArgumentException("Fact must match type of the rule's range", e);
                    }
                });
    }

    @Override
    public String toString() {
        return "RangeRule{" +
               "type='" + getType() + '\'' +
               ", description='" + getDescription() + '\'' +
               ", key='" + getKey() + '\'' +
               ", operator=" + getOperator() +
               ", min=" + min +
               ", max=" + max +
               ", minExclusive=" + minExclusive +
               ", maxExclusive=" + maxExclusive +
               ", ignore=" + isIgnore() +
               "}";
    }
}
