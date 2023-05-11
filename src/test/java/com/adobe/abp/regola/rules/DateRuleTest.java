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

import com.adobe.abp.regola.actions.Action;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.ValuesRuleResult;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testing DateRule with operator")
class DateRuleTest {

    private DateRule rule;
    private final FactsResolver resolver = mock(FactsResolver.class);

    private static final String RULE_KEY = "date_fact";
    private static final OffsetDateTime INVALID_FACT = makeDate("3000-12-03T08:00:00Z");

    @BeforeEach
    void setup() {
        rule = new DateRule();
        rule.setKey(RULE_KEY);
    }

    @Test
    @DisplayName("have the expected DATE type")
    void ruleHasExpectedType() {
        assertThat(rule.getType()).isEqualTo("DATE");
    }

    private static OffsetDateTime makeDate(String date) {
        return OffsetDateTime.parse(date, ISO_DATE_TIME);
    }

    @Nested
    @DisplayName("equals should")
    class Equals {

        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.EQUALS);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = Operator.EQUALS;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-03T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> INVALID_FACT));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    INVALID_FACT, makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match the null value of the rule")
        void factIsNotValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, makeDate("2021-12-03T08:09:10Z"));
        }
    }

    @Nested
    @DisplayName("greater-than should")
    class GreaterThan {

        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.GREATER_THAN);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = Operator.GREATER_THAN;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-04T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-04T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-02T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    makeDate("2021-12-02T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match (same value)")
        void factIsNotValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    makeDate("2021-12-03T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as valid if fact exists and rule has a null value")
        void factIsValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, makeDate("2021-12-03T08:09:10Z"));
        }
    }

    @Nested
    @DisplayName("greater-than-equal should")
    class GreaterThanEqual {

        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.GREATER_THAN_EQUAL);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = Operator.GREATER_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-04T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-04T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-02T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    makeDate("2021-12-02T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as valid if fact does match (same value)")
        void factIsValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-03T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as valid if fact exists and rule has a null value")
        void factIsValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, makeDate("2021-12-03T08:09:10Z"));
        }
    }

    @Nested
    @DisplayName("less-than should")
    class LessThan {

        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.LESS_THAN);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = Operator.LESS_THAN;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-02T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-02T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-04T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    makeDate("2021-12-04T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match (same value)")
        void factIsNotValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    makeDate("2021-12-03T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as valid if fact exists and rule has a null value")
        void factIsValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID, makeDate("2021-12-03T08:09:10Z"));
        }
    }

    @Nested
    @DisplayName("less-than-equal should")
    class LessThanEqual {

        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.LESS_THAN_EQUAL);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = Operator.LESS_THAN_EQUAL;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-02T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-02T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-04T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    makeDate("2021-12-04T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as valid if fact does match (same value)")
        void factIsValidWithSameValue() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-03T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact exists and rule has a null value")
        void factIsValidOnNullRuleValue() {
            rule.setValue(null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTestWithNullValue(rule, resolver, ruleResultBuilder, Result.INVALID,
                    makeDate("2021-12-03T08:09:10Z"));
        }
    }

    @Nested
    @DisplayName("contains should")
    class Contains {

        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        @BeforeEach
        void setup() {
            rule.setOperator(Operator.CONTAINS);
            rule.setValue(makeDate("2021-12-03T08:00:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = Operator.CONTAINS;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:00:10Z");
            });
        }

        @Test
        @DisplayName("evaluate as valid if fact matches")
        void factIsValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(
                            makeDate("2021-12-01T08:00:10Z"),
                            makeDate("2021-12-03T08:00:10Z"), // matching value
                            makeDate("2021-12-05T08:00:10Z")
                    )));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    Set.of(makeDate("2021-12-01T08:00:10Z"), makeDate("2021-12-03T08:00:10Z"), makeDate("2021-12-05T08:00:10Z")),
                    makeDate("2021-12-03T08:00:10Z"));
        }

        @Test
        @DisplayName("evaluate as invalid if fact does not match")
        void factIsNotValid() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> Set.of(
                            makeDate("2021-12-01T08:00:10Z"),
                            makeDate("2021-12-30T08:00:10Z"), // not matching value
                            makeDate("2021-12-05T08:00:10Z")
                    )));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID,
                    Set.of(makeDate("2021-12-01T08:00:10Z"), makeDate("2021-12-30T08:00:10Z"), makeDate("2021-12-05T08:00:10Z")),
                    makeDate("2021-12-03T08:00:10Z"));
        }

        @Test
        @DisplayName("evaluate as failed if fact is a list")
        void factIsFailedIfProvidingAList() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> List.of(
                            makeDate("2021-12-01T08:00:10Z"),
                            makeDate("2021-12-03T08:00:10Z"), // matching value
                            makeDate("2021-12-05T08:00:10Z")
                    )));

            final var snapshot = RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                    "java.lang.IllegalArgumentException: Fact must be a Set");
            assertThat(snapshot.getCause())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Fact must be a Set");
        }

        @Test
        @DisplayName("evaluate as invalid if fact is null")
        void factIsFailedIfNull() {
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }
    }

    @Nested
    @DisplayName("not-supported should")
    class NotSupported {
        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as not supported")
        void factIsNotValidOnEmptyFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as not supported even if both fact and rule value are null")
        void factAndRuleValueAreNull(Operator operator) {
            setup(operator);

            rule.setValue(null);
            ruleResultBuilder = ruleResultBuilder.with(r -> r.expectedValue = null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }
    }

    @Nested
    @DisplayName("any-supported-rule should")
    class GenericTests {
        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;

        void setup(Operator operator) {
            rule.setOperator(operator);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @Test
        @DisplayName("evaluate and return description in result")
        void descriptionInResult() {
            setup(Operator.EQUALS);
            rule.setDescription("Date should match");
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));
            ruleResultBuilder = ruleResultBuilder.with(r -> r.description = "Date should match");

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-03T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"})
        @DisplayName("evaluate as invalid if fact and value are both null")
        void nullFactIsInvalidIfRuleValueIsNull(Operator operator) {
            setup(operator);

            rule.setValue(null);
            ruleResultBuilder = ruleResultBuilder.with(r -> r.expectedValue = null);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"})
        @DisplayName("evaluate as invalid if fact is null")
        void factIsNotValidOnNullFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> null));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.INVALID);
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"})
        @DisplayName("evaluate as failed if fact is non-valid date")
        void factIsNotValidOnBadFormattedFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03")));

            final var snapshot = RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                    "java.time.format.DateTimeParseException: Text '2021-12-03' could not be parsed at index 10");
            assertThat(snapshot.getCause())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(DateTimeParseException.class)
                    .hasMessageContaining("Text '2021-12-03' could not be parsed at index 10");
        }

        @Test
        @DisplayName("evaluate action on completion")
        void actionOnCompletion() {
            setup(Operator.EQUALS);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> makeDate("2021-12-03T08:09:10Z")));

            var integerAtomicReference = new AtomicInteger(0);
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> integerAtomicReference.getAndIncrement());
            rule.setAction(action);

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.VALID,
                    makeDate("2021-12-03T08:09:10Z"), makeDate("2021-12-03T08:09:10Z"));

            assertThat(integerAtomicReference.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("evaluate action on completion with exception")
        void actionOnCompletionWithException() {
            setup(Operator.EQUALS);
            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("Failed to fetch fact")));

            var throwableAtomicReference = new AtomicReference<Throwable>();
            var action = new Action().setOnCompletion((result, throwable, ruleResult) -> throwableAtomicReference.set(throwable));
            rule.setAction(action);

            RuleTestUtils.evaluateAndTestWithMessage(rule, resolver, ruleResultBuilder, Result.FAILED,
                    "java.lang.IllegalArgumentException: Failed to fetch fact");

            assertThat(throwableAtomicReference.get()).hasMessageContaining("Failed to fetch fact");
        }
    }

    @Nested
    @DisplayName("with custom executor should")
    class WithCustomExecutor {

        private ValuesRuleResult.RuleResultBuilder<OffsetDateTime> ruleResultBuilder;
        private final Executor executor = Executors.newCachedThreadPool();

        void setup(Operator operator) {
            rule = new DateRule(executor);
            rule.setKey(RULE_KEY);
            rule.setOperator(operator);
            rule.setValue(makeDate("2021-12-03T08:09:10Z"));

            ruleResultBuilder = ValuesRuleResult.<OffsetDateTime>builder().with(r -> {
                r.type = RuleType.DATE.getName();
                r.operator = operator;
                r.key = RULE_KEY;
                r.expectedValue = makeDate("2021-12-03T08:09:10Z");
            });
        }

        @ParameterizedTest
        @EnumSource(value = Operator.class,
                names = {"EQUALS", "GREATER_THAN", "GREATER_THAN_EQUAL", "LESS_THAN", "LESS_THAN_EQUAL", "CONTAINS"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("evaluate as not supported")
        void factIsNotValidOnEmptyFact(Operator operator) {
            setup(operator);

            when(resolver.resolveFact(RULE_KEY))
                    .thenReturn(CompletableFuture.supplyAsync(() -> "any"));

            RuleTestUtils.evaluateAndTest(rule, resolver, ruleResultBuilder, Result.OPERATION_NOT_SUPPORTED);
        }
    }

}
