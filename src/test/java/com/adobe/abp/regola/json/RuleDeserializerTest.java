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
import com.adobe.abp.regola.mockrules.MockRule;
import com.adobe.abp.regola.rules.DateRule;
import com.adobe.abp.regola.rules.ExistsRule;
import com.adobe.abp.regola.rules.KeyBasedRule;
import com.adobe.abp.regola.rules.NumberRule;
import com.adobe.abp.regola.rules.Operator;
import com.adobe.abp.regola.rules.OperatorBasedRule;
import com.adobe.abp.regola.rules.Rule;
import com.adobe.abp.regola.rules.SetRule;
import com.adobe.abp.regola.rules.SingleValueRule;
import com.adobe.abp.regola.rules.StringRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RuleDeserializerTest {

    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new RuleModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Nested
    @DisplayName("Rules for Data Types should deserialize")
    class DataTypeRule {

        @Test
        @DisplayName("a string rule")
        void deserializeStringRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "string-rule.json");
            StringRule rule = mapper.readValue(jsonRule, StringRule.class);

            assertThat(rule)
                    .extracting(Rule::getType, OperatorBasedRule::getOperator, KeyBasedRule::getKey, SingleValueRule::getValue)
                    .containsExactly("STRING", Operator.EQUALS, "foo", "bar");
        }

        @Test
        @DisplayName("a number<integer> rule")
        void deserializeNumberIntegerRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "number-integer-rule.json");
            NumberRule<Integer> rule = mapper.readValue(jsonRule, new TypeReference<>() {});

            assertThat(rule)
                    .extracting(Rule::getType, OperatorBasedRule::getOperator, KeyBasedRule::getKey, SingleValueRule::getValue)
                    .containsExactly("NUMBER", Operator.EQUALS, "foo", 7);
        }

        @Test
        @DisplayName("a number<double> rule")
        void deserializeNumberDoubleRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "number-double-rule.json");
            NumberRule<Double> rule = mapper.readValue(jsonRule, new TypeReference<>() {});

            assertThat(rule)
                    .extracting(Rule::getType, OperatorBasedRule::getOperator, KeyBasedRule::getKey, SingleValueRule::getValue)
                    .containsExactly("NUMBER", Operator.EQUALS, "foo", 7.123);
        }

        @Test
        @DisplayName("a date rule")
        void deserializeDateRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "date-rule.json");
            DateRule rule = mapper.readValue(jsonRule, DateRule.class);

            assertThat(rule)
                    .extracting(Rule::getType, OperatorBasedRule::getOperator, KeyBasedRule::getKey, SingleValueRule::getValue)
                    .containsExactly("DATE", Operator.LESS_THAN, "foo", OffsetDateTime.parse("2021-12-03T08:00:00Z", ISO_DATE_TIME));
        }

        @Test
        @DisplayName("with failure a partial date rule")
        void deserializePartialDateRuleFromJson_fails() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "date-partial-rule.json");

            assertThatExceptionOfType(InvalidFormatException.class)
                    .isThrownBy(() -> mapper.readValue(jsonRule, DateRule.class))
                    .withMessageContaining("Cannot deserialize value of type `java.time.OffsetDateTime` from String \"2021-12-03\"");
        }

        @Test
        @DisplayName("an exists rule")
        void deserializeExistsRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "exists-rule.json");
            ExistsRule rule = mapper.readValue(jsonRule, ExistsRule.class);

            assertThat(rule)
                    .extracting(Rule::getType, KeyBasedRule::getKey)
                    .containsExactly("EXISTS", "foo");
        }

        @Test
        @DisplayName("a string set rule")
        void deserializeStringSetRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "string-set-rule.json");
            SetRule<String> rule = mapper.readValue(jsonRule, new TypeReference<>() {});

            assertThat(rule)
                    .extracting(Rule::getType, OperatorBasedRule::getOperator, KeyBasedRule::getKey, SetRule::getValues)
                    .containsExactly("SET", Operator.IN, "foo", Set.of("bar", "baz"));
        }

        @Test
        @DisplayName("an integer set rule")
        void deserializeNumberSetRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "integer-set-rule.json");
            SetRule<Integer> rule = mapper.readValue(jsonRule, new TypeReference<>() {});

            assertThat(rule)
                    .extracting(Rule::getType, OperatorBasedRule::getOperator, KeyBasedRule::getKey, SetRule::getValues)
                    .containsExactly("SET", Operator.IN, "foo", Set.of(1, 2, 3));
        }
    }

    @Nested
    @DisplayName("Generic deserialization tests - should")
    class GenericTests {

        @Test
        @DisplayName("deserialize json of composite rules")
        void deserializeRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "composite-rule.json");
            Rule rule = mapper.readValue(jsonRule, Rule.class);

            assertThat(rule).isNotNull();
        }

        @Test
        @DisplayName("fail to deserialize an unknown rule")
        void deserializeUnknownRuleFromJson_fails() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "unknown-rule.json");
            Rule rule = mapper.readValue(jsonRule, Rule.class);

            assertThat(rule).isNull();
        }
    }

    @Nested
    @DisplayName("Deserialization tests for custom rules - should")
    class CustomRules {

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new RuleModule()
                        .addRule("MOCK_RULE", MockRule.class))
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        @Test
        @DisplayName("deserialize json of MockRule")
        void deserializeRuleFromJson() throws IOException {
            String jsonRule = TestUtils.readRules(getClass(), "mock-rule.json");
            Rule rule = mapper.readValue(jsonRule, Rule.class);

            assertThat(rule)
                    .isNotNull()
                    .isOfAnyClassIn(MockRule.class);
        }
    }
}