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

package com.adobe.abp.regola.facts;

import com.adobe.abp.regola.datafetchers.Context;
import com.adobe.abp.regola.datafetchers.DataFetcher;
import com.adobe.abp.regola.datafetchers.FetchResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Testing the SimpleFactsResolver")
class SimpleFactsResolverTest {
    
    private static final String TEST_KEY = "test-key";

    static class MockData {

        private final String foo;

        public MockData(String foo) {
            this.foo = foo;
        }

        public String getFoo() {
            return foo;
        }
    }

    static class MockDataFetcher extends DataFetcher<MockData, Context> {

        private final MockData data;

        public MockDataFetcher(MockData data) {
            this.data = data;
        }

        @Override
        public CompletableFuture<FetchResponse<MockData>> fetchResponse(Context context) {
            FetchResponse<MockData> response = new FetchResponse<>();
            response.setData(data);
            return CompletableFuture.supplyAsync(() -> response);
        }
    }

    private enum TestDataSources implements DataSource {
        MOCK
    }


    @Nested
    @DisplayName("with facts without associated data fetcher")
    class NoDataFetchingTests {

        @Test
        @DisplayName("should resolve a key to a data point if a fact was added for that key")
        void addedFactIsResolvable() {
            SimpleFactsResolver<?> factsResolver = new SimpleFactsResolver<>();
            factsResolver.addFact(new Fact<>(TEST_KEY, StandardDataSources.NONE, data -> "ETLA"));

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("ETLA");
        }

        @Test
        @DisplayName("should resolve a key to a data point if a fact, with implicit data source, was added for that key")
        void addedFactWithImplicitDataSourceIsResolvable() {
            SimpleFactsResolver<?> factsResolver = new SimpleFactsResolver<>();
            factsResolver.addFact(new Fact<>(TEST_KEY, data -> "ETLA"));

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("ETLA");
        }

        @Test
        @DisplayName("should resolve a key to a null data point if a fact was not added for that key")
        void unknownFactIsResolvesToNull() {
            SimpleFactsResolver<?> factsResolver = new SimpleFactsResolver<>();

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .succeedsWithin(Duration.ofMillis(50))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("with facts with associated data fetcher")
    class DataFetchingTests {
        private final Context context = mock(Context.class);

        @Test
        @DisplayName("should resolve a key to a data point if a fact was added for that key")
        void addedFactIsResolvable() {
            DataFetcher<MockData, Context> dataFetcher = spy(new MockDataFetcher(new MockData("ETLA")));
            SimpleFactsResolver<Context> factsResolver = new SimpleFactsResolver<>(context, Map.of(
                    TestDataSources.MOCK, dataFetcher
            ));
            factsResolver.addFact(new Fact<>(TEST_KEY, TestDataSources.MOCK, MockData::getFoo));
            verifyNoInteractions(dataFetcher);

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("ETLA");
            verify(dataFetcher).fetch(context);
        }

        @Test
        @DisplayName("should resolve a key to a null data point if a fact was not added for that key")
        void unknownFactIsResolvesToNull() {
            DataFetcher<MockData, Context> dataFetcher = mock(DataFetcher.class);
            SimpleFactsResolver<Context> factsResolver = new SimpleFactsResolver<>(context, Map.of(
                    TestDataSources.MOCK, dataFetcher
            ));

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .succeedsWithin(Duration.ofMillis(50))
                    .isNull();
            verifyNoInteractions(dataFetcher);
        }

        @Test
        @DisplayName("should not resolve a key to a data point if a fact is mapped to an unknown data fetcher and needs data object")
        void addedFactIsNotResolvableIfMappedToUnknownDataFetcherAndNeedsDataObject() {
            DataFetcher<MockData, Context> dataFetcher = spy(new MockDataFetcher(new MockData("ETLA")));
            SimpleFactsResolver<Context> factsResolver = new SimpleFactsResolver<>(context, Map.of());
            factsResolver.addFact(new Fact<>(TEST_KEY, TestDataSources.MOCK, MockData::getFoo));
            verifyNoInteractions(dataFetcher);

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .failsWithin(Duration.ofMillis(50))
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(NullPointerException.class);
            verifyNoInteractions(dataFetcher);
        }

        @Test
        @DisplayName("should not resolve a key to a data point if a fact is mapped to an unknown data fetcher")
        void addedFactIsNotResolvableIfMappedToUnknownDataFetcher() {
            DataFetcher<MockData, Context> dataFetcher = spy(new MockDataFetcher(new MockData("ETLA")));
            SimpleFactsResolver<Context> factsResolver = new SimpleFactsResolver<>(context, Map.of());
            factsResolver.addFact(new Fact<>(TEST_KEY, TestDataSources.MOCK, data -> "XYZ"));
            verifyNoInteractions(dataFetcher);

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("XYZ");
            verifyNoInteractions(dataFetcher);
        }
    }
}
