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

import com.adobe.abp.regola.datafetchers.DataFetcher;
import com.adobe.abp.regola.evaluators.Evaluator;
import com.adobe.abp.regola.facts.DataSource;
import com.adobe.abp.regola.facts.Fact;
import com.adobe.abp.regola.facts.FactsResolver;
import com.adobe.abp.regola.facts.SimpleFactsResolver;
import com.adobe.abp.regola.facts.TestDataSources;
import com.adobe.abp.regola.json.RuleModule;
import com.adobe.abp.regola.mockdatafetchers.FixedDelayedDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.FixedDelayedWithSlaDataFetcher;
import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.rules.Operator;
import com.adobe.abp.regola.rules.Rule;
import com.adobe.abp.regola.rules.StringRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

// Generic integration tests
public class RegolaIT {

    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new RuleModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void evaluatingBasicRule() throws JsonProcessingException {
        var stringRule = new StringRule()
                .setValue("COM")
                .setOperator(Operator.EQUALS)
                .setKey("MARKET_SEGMENT")
                .setDescription("Check that the market segment is COM");

        FactsResolver factsResolver = new SimpleFactsResolver<>();
        factsResolver.addFact(new Fact<>("MARKET_SEGMENT", data -> "COM"));

        final var evaluationResult = new Evaluator().evaluate(stringRule, factsResolver);
        evaluationResult.status().join();
        final var result = evaluationResult.snapshot();
        assertThat(result.getResult()).isEqualTo(Result.VALID);

        // Printing the result
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @Test
    @Timeout(value = 1)
    void evaluateCompositeRule() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "composite-rule.json");
        Rule rule = mapper.readValue(jsonRule, Rule.class);

        final var slow = new FixedDelayedDataFetcher(300);
        final var faster = new FixedDelayedDataFetcher(100);
        Map<DataSource, DataFetcher<?, FixedDelayedDataFetcher.RemoteContext>> dataFetchers = Map.of(
                TestDataSources.SLOW, slow,
                TestDataSources.FASTER, faster
        );

        FactsResolver factsResolver = new SimpleFactsResolver<>(new FixedDelayedDataFetcher.RemoteContext(), dataFetchers);
        factsResolver.addFact(new Fact<>("MARKET_SEGMENT", TestDataSources.SLOW, data -> "COM"));
        factsResolver.addFact(new Fact<>("capacity", TestDataSources.FASTER, data -> 3));
        factsResolver.addFact(new Fact<>("release_date", data -> OffsetDateTime.parse("2030-12-03T08:00:00Z", ISO_DATE_TIME)));
        factsResolver.addFact(new Fact<>("profile", data -> "some random value"));

        StopWatch watch = StopWatch.createStarted();
        final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
        evaluationResult.status().join();
        final var result = evaluationResult.snapshot();
        assertThat(result.getResult()).isEqualTo(Result.VALID);

        // Printing results
        System.out.println("Evaluated in (ms): " + watch.getTime());
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @RepeatedTest(50)
    @Timeout(value = 1)
    void lockThreadIssue() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "lock-rule.json"); // this test will not complete if issue in and/not rules locking
        Rule rule = mapper.readValue(jsonRule, Rule.class);

        final var slow = new FixedDelayedDataFetcher(100);
        Map<DataSource, DataFetcher<?, FixedDelayedDataFetcher.RemoteContext>> dataFetchers = Map.of(
                TestDataSources.SLOW, slow
        );

        FactsResolver factsResolver = new SimpleFactsResolver<>(new FixedDelayedDataFetcher.RemoteContext(), dataFetchers);
        // This fact should not return a map. Hence the rule should FAIL.
        factsResolver.addFact(new Fact<>("MARKET_SEGMENT", TestDataSources.SLOW, data -> Map.of("COM", "COM")));

        final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
        evaluationResult.status().join();
        final var result = evaluationResult.snapshot();
        assertThat(result.getResult()).isEqualTo(Result.FAILED);
    }

    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);

    @RepeatedTest(10)
    @Timeout(value = 1)
    void inspectRunningRule() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "inspect-running-rule.json");
        Rule rule = mapper.readValue(jsonRule, Rule.class);

        final var slow = new FixedDelayedDataFetcher(500);
        Map<DataSource, DataFetcher<?, FixedDelayedDataFetcher.RemoteContext>> dataFetchers = Map.of(
                TestDataSources.SLOW, slow
        );

        FactsResolver factsResolver = new SimpleFactsResolver<>(new FixedDelayedDataFetcher.RemoteContext(), dataFetchers);
        factsResolver.addFact(new Fact<>("MARKET_SEGMENT", TestDataSources.SLOW, data -> "COM"));

        final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
        final var future = executorService.scheduleAtFixedRate(() ->
                assertThat(evaluationResult.snapshot().getResult()).isEqualTo(Result.MAYBE), // Can inspect running rule even with read/write lock in place
                0, 100, TimeUnit.MILLISECONDS);

        evaluationResult.status().join();
        final var result = evaluationResult.snapshot();
        assertThat(result.getResult()).isEqualTo(Result.VALID);

        future.cancel(true);
    }

    @Test
    void dataFetcherFailingSla() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "failing-sla-rule.json");
        Rule rule = mapper.readValue(jsonRule, Rule.class);

        final var slow = spy(new FixedDelayedWithSlaDataFetcher(300, 250)); // 300 > 250, will fail SLA
        final var faster = spy(new FixedDelayedWithSlaDataFetcher(100, 150)); // 100 < 150, will not fail SLA
        Map<DataSource, DataFetcher<?, FixedDelayedWithSlaDataFetcher.RemoteContext>> dataFetchers = Map.of(
                TestDataSources.SLOW, slow,
                TestDataSources.FASTER, faster
        );

        FactsResolver factsResolver = new SimpleFactsResolver<>(new FixedDelayedWithSlaDataFetcher.RemoteContext(), dataFetchers);
        factsResolver.addFact(new Fact<>("MARKET_SEGMENT", TestDataSources.SLOW, data -> "COM"));
        factsResolver.addFact(new Fact<>("capacity", TestDataSources.FASTER, data -> 3));

        final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
        evaluationResult.status().join();
        final var result = evaluationResult.snapshot();
        assertThat(result.getResult()).isEqualTo(Result.VALID);

        verify(slow).whenFailingSlaFetchTime(anyString(), anyLong(), anyDouble());
        verify(faster, never()).whenFailingSlaFetchTime(anyString(), anyLong(), anyDouble());
    }

    @RepeatedTest(50) // Repeating test as combination of OR/NOT rule could expose issues if bad locking in rules
    @Timeout(value = 1)
    void evaluatingNullableRule() throws IOException {
        String jsonRule = TestUtils.readRules(getClass(), "nullable-rule.json");
        Rule rule = mapper.readValue(jsonRule, Rule.class);

        FactsResolver factsResolver = new SimpleFactsResolver<>();
        factsResolver.addFact(new Fact<>("capacity", data -> null));

        final var evaluationResult = new Evaluator().evaluate(rule, factsResolver);
        evaluationResult.status().join();
        final var result = evaluationResult.snapshot();
        assertThat(result.getResult()).isEqualTo(Result.VALID);
    }
}
