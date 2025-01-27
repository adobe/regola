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
import jdk.jfr.Experimental;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Experimental rule for evaluating facts against a range of values.
 * Supports basic range operations for comparable types.
 *
 * The rule supports the following operators:
 * <ul>
 * <li>{@link Operator#BETWEEN}</li>
 * <li>{@link Operator#IN}</li>
 * <li>{@link Operator#IS_BEFORE}</li>
 * <li>{@link Operator#IS_AFTER}</li>
 * </ul>
 *
 * @param <T> type of the fact (must extend Comparable)
 */
@Experimental
public class RangeRule<T extends Comparable<T>> extends OperatorBasedRule {

    private final Executor executor;
    private T min;
    private T max;
    private T step;
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

    public T getStep() {
        return step;
    }

    public RangeRule<T> setStep(T step) {
        this.step = step;
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

    @SuppressWarnings("unchecked")
    private T convertToTargetType(Number value) {
        if (min == null) {
            throw new IllegalStateException("Min value must be set to determine the target type");
        }

        if (min instanceof Integer) {
            return (T) Integer.valueOf(value.intValue());
        } else if (min instanceof Long) {
            return (T) Long.valueOf(value.longValue());
        } else if (min instanceof Double) {
            return (T) Double.valueOf(value.doubleValue());
        } else if (min instanceof Float) {
            return (T) Float.valueOf(value.floatValue());
        } else if (min instanceof Short) {
            return (T) Short.valueOf(value.shortValue());
        } else if (min instanceof Byte) {
            return (T) Byte.valueOf(value.byteValue());
        }

        throw new IllegalArgumentException("Unsupported numeric type: " + min.getClass());
    }

    private Collection<T> generateExpectedValues() {
        Collection<T> expectedValues = new ArrayList<>();

        switch (getOperator()) {
            case BETWEEN:
                expectedValues.add(min);
                expectedValues.add(max);
                break;
            case IN:
                if (step != null && min instanceof Number && max instanceof Number && step instanceof Number) {
                    double minVal = ((Number) min).doubleValue();
                    double maxVal = ((Number) max).doubleValue();
                    double stepVal = ((Number) step).doubleValue();

                    for (double i = minVal; i <= maxVal; i += stepVal) {
                        expectedValues.add(convertToTargetType(i));
                    }
                } else {
                    expectedValues.add(min);
                    expectedValues.add(max);
                }
                break;
            case IS_BEFORE:
                expectedValues.add(min);
                break;
            case IS_AFTER:
                expectedValues.add(max);
                break;
        }

        return expectedValues;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new EvaluationResult() {
            private Result result = Result.MAYBE;
            private T evaluatedFact;
            private Collection<T> evaluatedFacts;
            private String message;
            private Throwable cause;
            private final Collection<T> expectedValues = generateExpectedValues();

            @Override
            public RuleResult snapshot() {
                return ValuesRuleResult.<T>builder().with(r -> {
                    r.type = getType();
                    r.key = getKey();
                    r.operator = getOperator();
                    r.description = getDescription();
                    r.result = result;
                    r.actualValue = evaluatedFact;
                    r.actualValues = evaluatedFacts;
                    r.expectedValues = expectedValues;
                    if (!expectedValues.isEmpty()) {
                        r.expectedValue = expectedValues.iterator().next();
                    }
                    r.message = message;
                    r.cause = cause;
                    r.ignored = isIgnore();
                }).build();
            }

            @Override
            public CompletableFuture<Result> status() {
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

            private Result handleFailure(Throwable throwable) {
                result = Result.FAILED;
                message = throwable.getMessage();
                cause = throwable;
                return result;
            }
        };
    }

    private Result evaluateRange(T fact) {
        switch (getOperator()) {
            case BETWEEN:
                return evaluateBetween(fact);
            case IN:
                return evaluateIn(fact);
            case IS_BEFORE:
                return evaluateIsBefore(fact);
            case IS_AFTER:
                return evaluateIsAfter(fact);
            default:
                return Result.OPERATION_NOT_SUPPORTED;
        }
    }

    private Result evaluateBetween(T fact) {
        int compareMin = fact.compareTo(min);
        int compareMax = fact.compareTo(max);

        boolean afterMin = minExclusive ? compareMin > 0 : compareMin >= 0;
        boolean beforeMax = maxExclusive ? compareMax < 0 : compareMax <= 0;

        return afterMin && beforeMax ? Result.VALID : Result.INVALID;
    }

    private Result evaluateIn(T fact) {
        if (step == null) {
            return evaluateBetween(fact);
        }

        if (fact instanceof Number && step instanceof Number) {
            double value = ((Number) fact).doubleValue();
            double minVal = ((Number) min).doubleValue();
            double stepVal = ((Number) step).doubleValue();

            if ((value - minVal) % stepVal == 0) {
                return evaluateBetween(fact);
            }
        }

        return Result.INVALID;
    }

    private Result evaluateIsBefore(T fact) {
        int compareMin = fact.compareTo(min);
        return compareMin < 0 ? Result.VALID : Result.INVALID;
    }

    private Result evaluateIsAfter(T fact) {
        int compareMax = fact.compareTo(max);
        return compareMax > 0 ? Result.VALID : Result.INVALID;
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
                ", step=" + step +
                ", minExclusive=" + minExclusive +
                ", maxExclusive=" + maxExclusive +
                ", ignore=" + isIgnore() +
                "}";
    }
}