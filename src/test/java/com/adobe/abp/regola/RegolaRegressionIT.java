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

import com.adobe.abp.regola.datafetchers.Context;
import com.adobe.abp.regola.datafetchers.DataFetcher;
import com.adobe.abp.regola.evaluators.Evaluator;
import com.adobe.abp.regola.facts.DataSource;
import com.adobe.abp.regola.facts.Fact;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.facts.SimpleFactsResolver;
import com.adobe.abp.regola.facts.TestDataSources;
import com.adobe.abp.regola.json.RuleModule;
import com.adobe.abp.regola.mockdatafetchers.EmptyObjectDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.FixedDelayedContextCachedDataFetcher;
import com.adobe.abp.regola.rules.Rule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.RepeatedTest;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;

// Basic regression tests
public class RegolaRegressionIT {

    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new RuleModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @RepeatedTest(10)
    void regressionOnEvaluatingCompositeRule() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "composite-rule.json");
        Rule rule = mapper.readValue(jsonRule, Rule.class);

        // Use a data fetcher that will use caching across requests
        final var slow = new FixedDelayedContextCachedDataFetcher(300);
        final var faster = new FixedDelayedContextCachedDataFetcher(100);
        Map<DataSource, DataFetcher<?, FixedDelayedContextCachedDataFetcher.RemoteContext>> dataFetchers = Map.of(
                TestDataSources.SLOW, slow,
                TestDataSources.FASTER, faster
        );

        FactsResolver factsResolver = new SimpleFactsResolver<>(new FixedDelayedContextCachedDataFetcher.RemoteContext(), dataFetchers);
        factsResolver.addFact(new Fact<>("MARKET_SEGMENT", TestDataSources.SLOW, data -> "COM"));
        factsResolver.addFact(new Fact<>("capacity", TestDataSources.FASTER, data -> 3));
        factsResolver.addFact(new Fact<>("release_date", data -> OffsetDateTime.parse("2030-12-03T08:00:00Z", ISO_DATE_TIME)));
        factsResolver.addFact(new Fact<>("profile", data -> "whatever value: checking exists"));

        StopWatch watch = StopWatch.createStarted();

        for (int i = 0; i < 1_000; i++) {
            final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
            evaluationResult.status().join();
        }

        // Make sure we are sub-second.
        final var end = watch.getDuration().toMillis();
        System.out.printf("Evaluated 1_000 rules in %d ms - Average time to evaluate one rule %.3f ms\n", end, end/1_000.0);
        assertThat(end).isLessThan(1_000);
    }

    @RepeatedTest(10)
    void regressionOnEvaluatingCompositeRuleWithZeroDelayFetchers() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "composite-rule.json");
        Rule rule = mapper.readValue(jsonRule, Rule.class);

        // Use a data fetcher that will use caching across requests
        final var slow = new EmptyObjectDataFetcher();
        final var faster = new EmptyObjectDataFetcher();
        Map<DataSource, DataFetcher<?, Context>> dataFetchers = Map.of(
                TestDataSources.SLOW, slow,
                TestDataSources.FASTER, faster
        );

        SimpleFactsResolver<?> factsResolver = new SimpleFactsResolver<>(new Context(){}, dataFetchers);
        factsResolver.addFact(new Fact<>("MARKET_SEGMENT", TestDataSources.SLOW, data -> "COM"));
        factsResolver.addFact(new Fact<>("capacity", TestDataSources.FASTER, data -> 3));
        factsResolver.addFact(new Fact<>("release_date", data -> OffsetDateTime.parse("2030-12-03T08:00:00Z", ISO_DATE_TIME)));
        factsResolver.addFact(new Fact<>("profile", data -> "whatever value: checking exists"));

        StopWatch watch = StopWatch.createStarted();

        for (int i = 0; i < 10_000; i++) {
            final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
            evaluationResult.status().join();
        }

        // Make sure we are sub-second.
        final var end = watch.getDuration().toMillis();
        System.out.printf("Evaluated 10_000 rules in %d ms - Average time to evaluate one rule %.3f ms\n", end, end/10_000.0);
        assertThat(end).isLessThan(1_000);
    }
}
