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
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    private static final String TYPE_MISMATCH = "Fact must match type of the rule's range";

    private final List<Operator> SUPPORTED_OPERATORS = List.of(Operator.BETWEEN, Operator.IS_BEFORE, Operator.IS_AFTER);

    private final Executor executor;
    private T min;
    private T max;
    private boolean minExclusive;
    private boolean maxExclusive;

    public RangeRule() {
        this(null);
    }

    /**
     * Construct a range rule.
     *
     * <p>{@code executor} runs the check after the fact is resolved, and the
     * {@code OPERATION_NOT_SUPPORTED} result for unsupported operators. It does not run fact resolution:
     * to isolate blocking data fetchers, supply an executor to
     * {@link com.adobe.abp.regola.facts.SimpleFactsResolver} instead.</p>
     *
     * @param executor executor for the check, or {@code null} to use
     *                 {@link java.util.concurrent.ForkJoinPool#commonPool()}
     */
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
            default:
                break;
        }

        values.removeIf(Objects::isNull);
        return values;
    }

    @Override
    public EvaluationResult evaluate(FactsResolver factsResolver) {
        return new LeafEvaluationResult<T>(this, factsResolver) {
            @Override
            CompletableFuture<LeafOutcome<T>> evaluateRule() {
                if (!SUPPORTED_OPERATORS.contains(getOperator())) {
                    return runCheckAsync(
                            () -> LeafOutcome.of(Result.OPERATION_NOT_SUPPORTED), executor);
                }

                return exposeTypeMismatch(castFact(resolveFact())
                        .thenCompose(fact -> runCheckAsync(() -> handleSuccess(fact), executor)));
            }

            @Override
            void describeExpectation(ValuesRuleResult.RuleResultBuilder<T> ruleResult) {
                ruleResult.operator = getOperator();
                ruleResult.expectedValues = generateExpectedValues();
            }

            private LeafOutcome<T> handleSuccess(T fact) {
                if (fact == null) {
                    return LeafOutcome.of(Result.INVALID);
                }

                return evaluateRange(fact);
            }

            private LeafOutcome<T> evaluateRange(T fact) {
                var operator = getOperator();

                if (operator == Operator.BETWEEN) {
                    if (min == null || max == null) {
                        return LeafOutcome.failed("min or max are null", fact);
                    }
                    return LeafOutcome.ofValue(evaluateBetween(fact), fact);
                }

                if (operator == Operator.IS_BEFORE) {
                    if (min == null) {
                        return LeafOutcome.failed("min is null", fact);
                    }
                    return LeafOutcome.ofValue(evaluateIsBefore(fact), fact);
                }

                if (operator == Operator.IS_AFTER) {
                    if (max == null) {
                        return LeafOutcome.failed("max is null", fact);
                    }
                    return LeafOutcome.ofValue(evaluateIsAfter(fact), fact);
                }

                return LeafOutcome.ofValue(Result.OPERATION_NOT_SUPPORTED, fact);
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
    private CompletableFuture<T> castFact(CompletableFuture<Object> resolved) {
        return resolved.thenApply(fact -> {
            if (fact == null) {
                return null;
            }
            requireMatchingFact(fact);
            return (T) fact;
        });
    }

    private void requireMatchingFact(Object fact) {
        Class<?> witness = rangeWitness();
        if (witness != null && !witness.isInstance(fact)) {
            throw new IllegalArgumentException(TYPE_MISMATCH);
        }
    }

    private Class<?> rangeWitness() {
        if (min != null) {
            return min.getClass();
        }
        if (max != null) {
            return max.getClass();
        }
        return null;
    }

    private CompletableFuture<LeafOutcome<T>> exposeTypeMismatch(
            CompletableFuture<LeafOutcome<T>> checked) {
        var exposed = new CompletableFuture<LeafOutcome<T>>();
        checked.whenComplete((outcome, failure) -> {
            if (failure == null) {
                exposed.complete(outcome);
                return;
            }
            exposed.completeExceptionally(typeMismatchOrSame(failure));
        });
        return exposed;
    }

    private Throwable typeMismatchOrSame(Throwable failure) {
        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
        if (cause instanceof IllegalArgumentException
                && TYPE_MISMATCH.equals(cause.getMessage())) {
            return cause;
        }
        return failure;
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
