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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleResultSerializationTest {

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
    void serializeMultiaryRuleResult() throws IOException {
        final var ruleResult = MultiaryBooleanRuleResult.builder().with(r -> {
            r.type = "Multiary-type";
            r.result = Result.VALID;
            r.rules = List.of(
                    valuesRuleResult
            );
        }).build();

        String jsonRule = TestUtils.readRules(getClass(), "multiary-result-rule.json");
        RuleResult expectedRule = mapper.readValue(jsonRule, new TypeReference<>() {});

        assertThat(ruleResult).isEqualTo(expectedRule);
    }

    @Test
    void serializeUnaryRuleResult() throws IOException {
        final var ruleResult = UnaryBooleanRuleResult.builder().with(r -> {
            r.type = "Unary-type";
            r.result = Result.VALID;
            r.rule = valuesRuleResult;
        }).build();

        String jsonRule = TestUtils.readRules(getClass(), "unary-result-rule.json");
        RuleResult expectedRule = mapper.readValue(jsonRule, new TypeReference<>() {});

        assertThat(ruleResult).isEqualTo(expectedRule);
    }
}
