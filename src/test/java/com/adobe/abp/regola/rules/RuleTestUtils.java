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

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleTestUtils {

    public static void evaluateAndTest(Rule rule, FactsResolver resolver,
                                           RuleResult.RuleResultBuilder ruleResultBuilder,
                                           Result expectedResult) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> r.result = expectedResult).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static <T> void evaluateAndTest(Rule rule, FactsResolver resolver,
                                           ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                           Result expectedResult) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> r.result = expectedResult).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static <T> void evaluateAndTest(Rule rule, FactsResolver resolver,
                                           ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                           Result expectedResult,
                                           T actualValue,
                                           T expectedValue) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.actualValue = actualValue;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static <T> void evaluateAndTest(Rule rule, FactsResolver resolver,
                                           ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                           Result expectedResult,
                                           Collection<T> actualValues,
                                           T expectedValue) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.actualValues = actualValues;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static <T> void evaluateAndTest(Rule rule, FactsResolver resolver,
                                           ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                           Result expectedResult,
                                           T actualValue,
                                           Collection<T> expectedValues) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValues = expectedValues;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.actualValue = actualValue;
            r.expectedValues = expectedValues;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static <T> void evaluateAndTest(Rule rule, FactsResolver resolver,
                                           ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                           Result expectedResult,
                                           Collection<T> actualValues,
                                           Collection<T> expectedValues) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValues = expectedValues;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.actualValues = actualValues;
            r.expectedValues = expectedValues;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static <T> void evaluateAndTest(Rule rule, FactsResolver resolver,
                                           ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                           Result expectedResult,
                                           T expectedValue) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static RuleResult evaluateAndTestWithMessage(Rule rule, FactsResolver resolver,
                                                        ValuesRuleResult.RuleResultBuilder<?> ruleResultBuilder,
                                                        Result expectedResult,
                                                        String expectedMessage) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> r.result = Result.MAYBE).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.message = expectedMessage;
        }).build();
        assertThat(evaluationResult.snapshot())
                .usingRecursiveComparison()
                .ignoringFields("cause")
                .isEqualTo(expectedFinalResult);
        return evaluationResult.snapshot();
    }

    public static <T> RuleResult evaluateAndTestWithMessage(Rule rule, FactsResolver resolver,
                                                            ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                                            Result expectedResult,
                                                            String expectedMessage,
                                                            T expectedValue) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.message = expectedMessage;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot())
                .usingRecursiveComparison()
                .ignoringFields("cause")
                .isEqualTo(expectedFinalResult);
        return evaluationResult.snapshot();
    }

    public static <T> RuleResult evaluateAndTestWithMessage(Rule rule, FactsResolver resolver,
                                                            ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                                            Result expectedResult,
                                                            String expectedMessage,
                                                            Collection<T> expectedValues) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValues = expectedValues;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.message = expectedMessage;
            r.expectedValues = expectedValues;
        }).build();
        assertThat(evaluationResult.snapshot())
                .usingRecursiveComparison()
                .ignoringFields("cause")
                .isEqualTo(expectedFinalResult);
        return evaluationResult.snapshot();
    }

    public static <T> void evaluateAndTestWithNullValue(Rule rule, FactsResolver resolver,
                                                        ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                                        Result expectedResult,
                                                        T actualValue) {
        evaluateAndTestWithValue(rule, resolver, ruleResultBuilder, expectedResult, actualValue, null);
    }

    public static <T> void evaluateAndTestWithValue(Rule rule, FactsResolver resolver,
                                                    ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                                    Result expectedResult,
                                                    T expectedValue) {
        evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder, expectedResult, null, expectedValue,null);
    }

    public static <T> void evaluateAndTestWithValue(Rule rule, FactsResolver resolver,
                                                    ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                                    Result expectedResult,
                                                    T actualValue,
                                                    T expectedValue) {
        evaluateAndTestWithValueAndMessage(rule, resolver, ruleResultBuilder, expectedResult, actualValue, expectedValue,null);
    }

    public static <T> void evaluateAndTestWithValueAndMessage(Rule rule, FactsResolver resolver,
                                                              ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                                              Result expectedResult,
                                                              T actualValue,
                                                              T expectedValue,
                                                              String message) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.actualValue = actualValue;
            r.expectedValue = expectedValue;
            r.message = message;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    // Use this method when the type of the actual value and expected value do not match (e.g. integer vs doubles)
    public static void evaluateAndTestWithAnyTypeValue(Rule rule, FactsResolver resolver,
                                                       ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilder,
                                                       Result expectedResult,
                                                       Object actualValue,
                                                       Object expectedValue) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.actualValue = actualValue;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    // Use this method when the type of the actual value and expected value do not match (e.g. integer vs doubles)
    public static void evaluateAndTestWithAnyTypeValue(Rule rule, FactsResolver resolver,
                                                       ValuesRuleResult.RuleResultBuilder<Object> ruleResultBuilder,
                                                       Result expectedResult,
                                                       Collection<Object> actualValues,
                                                       Object expectedValue) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.actualValues = actualValues;
            r.expectedValue = expectedValue;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }

    public static <T> void evaluateAndTestWithValues(Rule rule, FactsResolver resolver,
                                                     ValuesRuleResult.RuleResultBuilder<T> ruleResultBuilder,
                                                     Result expectedResult,
                                                     Set<T> values) {
        final var evaluationResult = rule.evaluate(resolver);

        final var expectedInterimResult = ruleResultBuilder.with(r -> {
            r.result = Result.MAYBE;
            r.expectedValues = values;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedInterimResult);

        final var result = evaluationResult.status().join();

        assertThat(result).isEqualTo(expectedResult);
        final var expectedFinalResult = ruleResultBuilder.with(r -> {
            r.result = expectedResult;
            r.expectedValues = values;
        }).build();
        assertThat(evaluationResult.snapshot()).isEqualTo(expectedFinalResult);
    }
}
