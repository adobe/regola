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

package com.adobe.abp.regola.datafetchers;

import com.adobe.abp.regola.datafetchers.cache.DataCache;
import com.adobe.abp.regola.datafetchers.metrics.MetricsAgent;
import com.adobe.abp.regola.mockdatafetchers.CachedDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.EmptyObjectDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.FixedDelayedDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.FixedDelayedDataFetcherWithLogging;
import com.adobe.abp.regola.mockdatafetchers.FixedDelayedWithSlaDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.FunctionDelayedDataFetcher;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataFetcherTest {

    @Nested
    class FetchWithMetrics {

        private final Context context = mock(Context.class);

        @Test
        @DisplayName("should fetch data with context")
        void fetchWithContext() throws ExecutionException, InterruptedException {
            final var dataFetcher = spy(new EmptyObjectDataFetcher());
            dataFetcher.fetch(context).get();

            verify(dataFetcher).fetchResponse(context);
        }
    }

    @Nested
    class Metrics {

        private final FixedDelayedDataFetcher.RemoteContext context = new FixedDelayedDataFetcher.RemoteContext();
        private final static int TIMES_TO_SAMPLE = 10;

        @Test
        @DisplayName("should return average fetch time after first fetch approximately as first collected time")
        void averageFetchTimeAsFirstMeasure() throws ExecutionException, InterruptedException {
            final var dataFetcher = new FixedDelayedDataFetcher(10);

            assertThat(dataFetcher.getAverageFetchTime()).isNaN();

            dataFetcher.fetch(context).get();

            assertThat(dataFetcher.getAverageFetchTime()).isEqualTo(10, Offset.strictOffset(5.0));
        }

        @Test
        @DisplayName("should return average fetch time even if less data of cap")
        void getAverageFetchTimeEvenWhenLessOfSamplesToConsider() throws ExecutionException, InterruptedException {
            final var dataFetcher = new FixedDelayedDataFetcher(10);

            for (int i = 0; i < TIMES_TO_SAMPLE - 1; i++) {
                dataFetcher.fetch(context).get();
            }

            assertThat(dataFetcher.getAverageFetchTime()).isEqualTo(10, Offset.strictOffset(5.0));
        }

        @Test
        @DisplayName("should return the average fetch time if enough data")
        void averageFetchTimeGivenEnoughData() throws ExecutionException, InterruptedException {
            final var dataFetcher = new FixedDelayedDataFetcher(10);

            for (int i = 0; i < TIMES_TO_SAMPLE; i++) {
                dataFetcher.fetch(context).get();
            }

            assertThat(dataFetcher.getAverageFetchTime()).isEqualTo(10, Offset.strictOffset(5.0));
        }

        @RepeatedTest(10) // Repeating this test increases our ability to catch any synchronization issues on the collected data points
        @DisplayName("should return the average fetch time if enough data, but discard older data points")
        void averageFetchTimeButDiscardOldData() throws ExecutionException, InterruptedException {
            FunctionDelayedDataFetcher.RemoteContext context = new FunctionDelayedDataFetcher.RemoteContext();
            final var dataFetcher = new FunctionDelayedDataFetcher((iteration) -> iteration < TIMES_TO_SAMPLE ? 10 : 100);

            for (int i = 0; i < TIMES_TO_SAMPLE + 1; i++) {
                dataFetcher.fetch(context).get();
                context.incrementIteration();
            }

            assertThat(dataFetcher.getAverageFetchTime()).isGreaterThan(15);
        }
    }

    @Nested
    class CachingRequests {

        @Mock
        CompletableFuture<Object> future;

        @Mock
        DataCache<Object> cache;

        private final Context context = mock(Context.class);

        @Test
        @DisplayName("should fetch data from cache")
        void fetchWillCallCache() {
            when(cache.get(anyString(), any())).thenReturn(future);

            final var dataFetcher = new CachedDataFetcher(cache);

            assertThat(dataFetcher.fetch(context)).isEqualTo(future);
            verify(cache).get(anyString(), any());
        }

        @Test
        @DisplayName("should fetch data from cache on any N calls")
        void fetchWillCallCacheNTimes() {
            final var dataFetcher = new CachedDataFetcher(cache);

            dataFetcher.fetch(context);
            dataFetcher.fetch(context);
            dataFetcher.fetch(context);

            verify(cache, times(3)).get(anyString(), any());
        }
    }

    @Nested
    class SlaFetchTime {

        private final FixedDelayedWithSlaDataFetcher.RemoteContext context = mock(FixedDelayedWithSlaDataFetcher.RemoteContext.class);

        @Test
        @DisplayName("should trigger sla failed method")
        void fetchWillFailSla() throws ExecutionException, InterruptedException {
            final var dataFetcher = spy(new FixedDelayedWithSlaDataFetcher(20, 5));

            dataFetcher.fetch(context).get();

            verify(dataFetcher).whenFailingSlaFetchTime(anyString(), eq(5L), anyDouble());
        }

        @Test
        @DisplayName("should not trigger sla failed method as within parameters")
        void fetchWillNotFailSla() throws ExecutionException, InterruptedException {
            final var dataFetcher = spy(new FixedDelayedWithSlaDataFetcher(5, 20));

            dataFetcher.fetch(context).get();

            verify(dataFetcher, never()).whenFailingSlaFetchTime(anyString(), anyLong(), anyDouble());
        }
    }

    @Nested
    class LoggingMetricsInfo {

        private final FixedDelayedDataFetcherWithLogging.RemoteContext context = new FixedDelayedDataFetcherWithLogging.RemoteContext();

        @Nested
        class WithDefaultAgent {
            // This test is used to verify that logging is taking place. No proper assertions are put in place.
            @Test
            @DisplayName("should log metrics with default agent")
            void logInfoMetrics() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(20, 10, false);

                assertThat(dataFetcher.fetch(context))
                        .succeedsWithin(Duration.ofMillis(100));
            }

            // This test is used to verify that logging is taking place. No proper assertions are put in place.
            @Test
            @DisplayName("should log metrics on failures with default agent")
            void logInfoMetricsOnFailure() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(20, 50, true);

                assertThat(dataFetcher.fetch(context))
                        .failsWithin(Duration.ofMillis(100))
                        .withThrowableOfType(ExecutionException.class)
                        .withCauseInstanceOf(RuntimeException.class)
                        .withMessageContaining("Exception thrown during a test");
            }
        }

        @Nested
        class WithMockedAgent {

            private final MetricsAgent agent = mock(MetricsAgent.class);

            @Test
            @DisplayName("should log metrics - onSuccess")
            void logInfoMetrics() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(agent, 20, 50, false);

                assertThat(dataFetcher.fetch(context))
                        .succeedsWithin(Duration.ofMillis(100));

                verify(agent).onSuccess(eq("FixedDelayedDataFetcherWithLogging"), anyString(), anyLong());
                verify(agent, never()).onSlaBreach(anyString(), anyString(), anyLong(), anyDouble());
                verify(agent, never()).onFailure(anyString(), anyString(), any(), anyLong());
            }

            @Test
            @DisplayName("should log metrics - onSlaBreach")
            void logInfoMetricsOnSlaBreach() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(agent, 20, 10, false);

                assertThat(dataFetcher.fetch(context))
                        .succeedsWithin(Duration.ofMillis(100));

                verify(agent).onSuccess(eq("FixedDelayedDataFetcherWithLogging"), anyString(), anyLong());
                verify(agent).onSlaBreach(eq("FixedDelayedDataFetcherWithLogging"), anyString(), eq(10L), anyDouble());
                verify(agent, never()).onFailure(anyString(), anyString(), any(), anyLong());
            }

            @Test
            @DisplayName("should log metrics - onFailure")
            void logInfoMetricsOnFailure() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(agent, 20, 50, true);

                assertThat(dataFetcher.fetch(context))
                        .failsWithin(Duration.ofMillis(100));

                verify(agent, never()).onSuccess(anyString(), anyString(), anyLong());
                verify(agent, never()).onSlaBreach(anyString(), anyString(), anyLong(), anyDouble());
                verify(agent).onFailure(eq("FixedDelayedDataFetcherWithLogging"), anyString(), any(), anyLong());
            }

            @Test
            @DisplayName("should log metrics - onFailure and metrics agent fails too")
            void logInfoMetricsOnFailureWithMetricsAgentAlsoFailing() {
                doThrow(new RuntimeException("Metrics Agent has failed"))
                        .when(agent).onFailure(anyString(), anyString(), any(), anyLong());

                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(agent, 20, 50, true);

                assertThat(dataFetcher.fetch(context))
                        .failsWithin(Duration.ofMillis(100)); // CompletableFuture is completed with failure.

                verify(agent, never()).onSuccess(anyString(), anyString(), anyLong());
                verify(agent, never()).onSlaBreach(anyString(), anyString(), anyLong(), anyDouble());
                verify(agent).onFailure(eq("FixedDelayedDataFetcherWithLogging"), anyString(), any(), anyLong());
            }
        }
    }
}
