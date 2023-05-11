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

package com.adobe.abp.regola;

import com.adobe.abp.regola.evaluators.Evaluator;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.json.RuleModule;
import com.adobe.abp.regola.rules.Rule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

// Generic integration tests
@SuppressWarnings({"RedundantThrows", "unused"})
public class RegolaFuzzyIT {

    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new RuleModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @RepeatedTest(1_000)
    @Timeout(value = 1)
    void evaluateFuzzyRule() throws JsonProcessingException {
        FuzzyTestCaseGenerator generator = new FuzzyTestCaseGenerator();
        Rule rule = generator.generate();
        FactsResolver factsResolver = generator.getFactsResolver();

        // Comment out for debugging purposes
        // System.out.println("==== RULE ====");
        // System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rule));

        // Evaluate and collect result
        final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
        evaluationResult.status().join();
        final var result = evaluationResult.snapshot();
        assertThat(result).isNotNull();

        // Comment out for debugging purposes
        // System.out.println("==== RESULT ====");
        // System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

}
