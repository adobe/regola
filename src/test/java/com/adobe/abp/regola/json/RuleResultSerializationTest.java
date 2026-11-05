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

package com.adobe.abp.regola.json;

import com.adobe.abp.regola.TestUtils;
import com.adobe.abp.regola.results.MultiaryBooleanRuleResult;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import com.adobe.abp.regola.results.UnaryBooleanRuleResult;
import com.adobe.abp.regola.results.ValuesRuleResult;
import com.adobe.abp.regola.rules.Operator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class RuleResultSerializationTest {

    @Test
    void valueCollectionsAreIsolatedWithoutLosingSetOrListSemantics() throws IOException {
        final var expected = new LinkedHashSet<>(Arrays.asList("value", null));
        final var actual = new ArrayList<>(Arrays.asList("value", "value", null));
        final var result = new ValuesRuleResult<String>();
        result.setType("values");
        result.setExpectedValues(expected);
        result.setActualValues(actual);

        expected.clear();
        actual.clear();
        result.getExpectedValues().clear();
        result.getActualValues().clear();

        assertThat(result.getExpectedValues())
                .isEqualTo(new LinkedHashSet<>(Arrays.asList("value", null)));
        assertThat(result.getActualValues())
                .containsExactly("value", "value", null);
        assertThat(mapper.writeValueAsString(result))
                .contains("\"expectedValues\":[\"value\",null]",
                        "\"actualValues\":[\"value\",\"value\",null]");
    }

    @Test
    void childCollectionIsIsolatedAndStillAllowsNullEntries() {
        final var child = new RuleResult();
        final var children = new ArrayList<>(Arrays.asList(child, null));
        final var result = new MultiaryBooleanRuleResult();
        result.setRules(children);

        children.clear();
        result.getRules().clear();

        assertThat(result.getRules())
                .containsExactly(child, null);
    }

    @Test
    void nullCollectionsRemainAbsentFromJson() throws IOException {
        final var result = new ValuesRuleResult<String>();
        result.setActualValues(null);
        result.setExpectedValues(null);
        final var parent = new MultiaryBooleanRuleResult();
        parent.setRules(null);

        assertThat(mapper.writeValueAsString(result))
                .doesNotContain("actualValues", "expectedValues");
        assertThat(mapper.writeValueAsString(parent))
                .doesNotContain("rules");
    }

    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new RuleResultModule());

    final RuleResult valuesRuleResult = ValuesRuleResult.builder().with(r -> {
        r.type = "some-type";
        r.key = "some-key";
        r.operator = Operator.EQUALS;
        r.result = Result.VALID;
        r.expectedValue = "some-value";
        r.actualValue = "some-value";
    }).build();

    @Test
    void serializeRuleResult() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "result-rule.json");
        RuleResult expectedRule = mapper.readValue(jsonRule, new TypeReference<>() {});

        assertThat(valuesRuleResult).isEqualTo(expectedRule);
    }

    @Test
    void serializeSkippedAndCancelled() throws IOException {
        final var cancelled = ValuesRuleResult.builder().with(r -> {
            r.type = "some-type";
            r.key = "some-key";
            r.result = Result.MAYBE;
            r.cancelled = true;
        }).build();

        final var cancelledJson = mapper.writeValueAsString(cancelled);

        assertThat(mapper.writeValueAsString(valuesRuleResult))
                .contains("\"skipped\":false", "\"cancelled\":false");
        assertThat(cancelledJson)
                .contains("\"skipped\":false", "\"cancelled\":true");
        assertThat(mapper.readValue(cancelledJson, RuleResult.class))
                .isEqualTo(cancelled)
                .hasSameHashCodeAs(cancelled);
        assertThat(cancelled.hashCode())
                .isNotEqualTo(ValuesRuleResult.builder().with(r -> {
                    r.type = "some-type";
                    r.key = "some-key";
                    r.result = Result.MAYBE;
                }).build().hashCode());
    }

    @Test
    void serializeMultiaryRuleResult() throws IOException {
        final var cause = new IllegalStateException("boom");
        final var ruleResult = MultiaryBooleanRuleResult.builder().with(r -> {
            r.type = "Multiary-type";
            r.result = Result.VALID;
            r.cause = cause;
            r.rules = List.of(
                    valuesRuleResult
            );
        }).build();

        assertThat(ruleResult.getCause())
                .isSameAs(cause);

        String jsonRule = TestUtils.readRules(getClass(), "multiary-result-rule.json");
        RuleResult expectedRule = mapper.readValue(jsonRule, new TypeReference<>() {});
        expectedRule.setCause(cause);

        assertThat(ruleResult).isEqualTo(expectedRule);
    }

    @Test
    void serializeUnaryRuleResult() throws IOException {
        final var cause = new IllegalStateException("boom");
        final var ruleResult = UnaryBooleanRuleResult.builder().with(r -> {
            r.type = "Unary-type";
            r.result = Result.VALID;
            r.cause = cause;
            r.rule = valuesRuleResult;
        }).build();

        assertThat(ruleResult.getCause())
                .isSameAs(cause);

        String jsonRule = TestUtils.readRules(getClass(), "unary-result-rule.json");
        RuleResult expectedRule = mapper.readValue(jsonRule, new TypeReference<>() {});
        expectedRule.setCause(cause);

        assertThat(ruleResult).isEqualTo(expectedRule);
    }

    @Test
    void deserializeBaseConstantRuleResultWithoutRecursing() throws IOException {
        // No "rules", "rule" or "key": this is the shape a ConstantRule snapshot produces.
        // Previously, this fell through to mapper.treeToValue(root, RuleResult.class), which
        // re-selected this same deserializer and recursed until a StackOverflowError.
        final var result = mapper.readValue(
                "{\"type\":\"CONSTANT\",\"result\":\"VALID\",\"ignored\":true}", RuleResult.class);

        assertThat(result.getResult()).isEqualTo(Result.VALID);
        assertThat(result.getType()).isEqualTo("CONSTANT");
        assertThat(result.isIgnored()).isTrue();
        assertThat(result.isSkipped()).isFalse();
        assertThat(result.isCancelled()).isFalse();
    }

    @Test
    void rejectEmptyObjectInsteadOfRecursing() {
        // "{}" matches none of "rules"/"rule"/"key"/"result": it must be rejected explicitly,
        // not fall through to the same infinite-recursion path as above.
        assertThatExceptionOfType(MismatchedInputException.class)
                .isThrownBy(() -> mapper.readValue("{}", RuleResult.class))
                .withMessageContaining("RuleResult");
    }

    @Test
    void rejectMalformedShapeWithNeitherDispatchFieldNorResult() {
        assertThatExceptionOfType(MismatchedInputException.class)
                .isThrownBy(() -> mapper.readValue("{\"foo\":\"bar\"}", RuleResult.class))
                .withMessageContaining("RuleResult");
    }
}
