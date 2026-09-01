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
import com.adobe.abp.regola.datafetchers.cache.DataCache;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
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

    private enum SynchronousFailurePoint {
        REQUEST_KEY,
        CACHE,
        FETCH_RESPONSE
    }

    static class RecordingDataCache implements DataCache<MockData> {

        private final AtomicReference<Thread> invocationThread = new AtomicReference<>();

        @Override
        public CompletableFuture<MockData> get(
                String key,
                java.util.function.Function<String, CompletableFuture<MockData>> mappingFunction) {
            invocationThread.set(Thread.currentThread());
            return mappingFunction.apply(key);
        }
    }

    static class RecordingDataFetcher extends DataFetcher<MockData, Context> {

        private final AtomicReference<Thread> fetchThread = new AtomicReference<>();
        private final AtomicReference<Thread> requestKeyThread = new AtomicReference<>();
        private final AtomicReference<Thread> responseThread = new AtomicReference<>();

        RecordingDataFetcher(DataCache<MockData> cache) {
            super(cache);
        }

        @Override
        public CompletableFuture<MockData> fetch(Context context) {
            fetchThread.set(Thread.currentThread());
            return super.fetch(context);
        }

        @Override
        public String calculateRequestKey(Context context) {
            requestKeyThread.set(Thread.currentThread());
            return TEST_KEY;
        }

        @Override
        public CompletableFuture<FetchResponse<MockData>> fetchResponse(Context context) {
            responseThread.set(Thread.currentThread());
            FetchResponse<MockData> response = new FetchResponse<>();
            response.setData(new MockData("ETLA"));
            return CompletableFuture.completedFuture(response);
        }
    }

    static class SynchronouslyFailingDataFetcher extends DataFetcher<MockData, Context> {

        private final SynchronousFailurePoint failurePoint;

        SynchronouslyFailingDataFetcher(SynchronousFailurePoint failurePoint) {
            super((key, mappingFunction) -> {
                if (failurePoint == SynchronousFailurePoint.CACHE) {
                    throw failure(failurePoint);
                }
                return mappingFunction.apply(key);
            });
            this.failurePoint = failurePoint;
        }

        @Override
        public String calculateRequestKey(Context context) {
            if (failurePoint == SynchronousFailurePoint.REQUEST_KEY) {
                throw failure(failurePoint);
            }
            return TEST_KEY;
        }

        @Override
        public CompletableFuture<FetchResponse<MockData>> fetchResponse(Context context) {
            if (failurePoint == SynchronousFailurePoint.FETCH_RESPONSE) {
                throw failure(failurePoint);
            }
            return CompletableFuture.completedFuture(new FetchResponse<>());
        }

        private static IllegalStateException failure(SynchronousFailurePoint failurePoint) {
            return new IllegalStateException("Synchronous failure at " + failurePoint);
        }
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
        @Mock
        DataFetcher<MockData, Context> dataFetcher;

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

        @Test
        @DisplayName("should invoke the complete data fetch on the supplied executor")
        void suppliedExecutorRunsCompleteDataFetch() {
            Thread callerThread = Thread.currentThread();
            RecordingDataCache cache = new RecordingDataCache();
            RecordingDataFetcher dataFetcher = new RecordingDataFetcher(cache);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                SimpleFactsResolver<Context> factsResolver = new SimpleFactsResolver<>(
                        context, Map.of(TestDataSources.MOCK, dataFetcher), executor);
                factsResolver.addFact(new Fact<>(TEST_KEY, TestDataSources.MOCK, MockData::getFoo));

                assertThat(factsResolver.resolveFact(TEST_KEY))
                        .succeedsWithin(Duration.ofMillis(250))
                        .isEqualTo("ETLA");

                assertThat(dataFetcher.fetchThread.get()).isNotEqualTo(callerThread);
                assertThat(dataFetcher.requestKeyThread.get()).isSameAs(dataFetcher.fetchThread.get());
                assertThat(cache.invocationThread.get()).isSameAs(dataFetcher.fetchThread.get());
                assertThat(dataFetcher.responseThread.get()).isSameAs(dataFetcher.fetchThread.get());
            } finally {
                executor.shutdownNow();
            }
        }

        @ParameterizedTest(name = "should complete exceptionally for a synchronous failure in {0}")
        @EnumSource(SynchronousFailurePoint.class)
        void synchronousFetchInvocationFailuresBecomeFailedFutures(SynchronousFailurePoint failurePoint) {
            DataFetcher<MockData, Context> dataFetcher = new SynchronouslyFailingDataFetcher(failurePoint);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                SimpleFactsResolver<Context> factsResolver = new SimpleFactsResolver<>(
                        context, Map.of(TestDataSources.MOCK, dataFetcher), executor);
                factsResolver.addFact(new Fact<>(TEST_KEY, TestDataSources.MOCK, MockData::getFoo));

                CompletableFuture<Object> resolvedFact = factsResolver.resolveFact(TEST_KEY);

                assertThat(resolvedFact)
                        .failsWithin(Duration.ofMillis(250))
                        .withThrowableOfType(ExecutionException.class)
                        .withCauseInstanceOf(IllegalStateException.class)
                        .withMessageContaining(failurePoint.name());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("should invoke the complete data fetch directly without an executor")
        void noExecutorRunsCompleteDataFetchOnCallingThread() {
            Thread callerThread = Thread.currentThread();
            RecordingDataCache cache = new RecordingDataCache();
            RecordingDataFetcher dataFetcher = new RecordingDataFetcher(cache);
            SimpleFactsResolver<Context> factsResolver = new SimpleFactsResolver<>(
                    context, Map.of(TestDataSources.MOCK, dataFetcher));
            factsResolver.addFact(new Fact<>(TEST_KEY, TestDataSources.MOCK, MockData::getFoo));

            assertThat(factsResolver.resolveFact(TEST_KEY))
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("ETLA");

            assertThat(dataFetcher.fetchThread.get()).isSameAs(callerThread);
            assertThat(dataFetcher.requestKeyThread.get()).isSameAs(callerThread);
            assertThat(cache.invocationThread.get()).isSameAs(callerThread);
            assertThat(dataFetcher.responseThread.get()).isSameAs(callerThread);
        }
    }
}
