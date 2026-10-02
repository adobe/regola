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
import com.adobe.abp.regola.datafetchers.cache.DataCacheConfiguration;
import com.adobe.abp.regola.datafetchers.metrics.MetricsAgent;
import com.adobe.abp.regola.mockdatafetchers.CachedDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.EmptyObjectDataFetcher;
import com.adobe.abp.regola.mockdatafetchers.FixedDelayedDataFetcherWithLogging;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

        private static final int TIMES_TO_SAMPLE = 10;
        private final Context context = mock(Context.class);
        private final List<Long> durations = new ArrayList<>();

        @Mock
        private MetricsAgent agent;

        @Test
        @DisplayName("should return the first recorded duration after the first fetch")
        void averageFetchTimeAsFirstMeasure() throws ExecutionException, InterruptedException {
            final var dataFetcher = createFetcher();

            assertThat(dataFetcher.getAverageFetchTime())
                    .isNaN();

            fetchSample(dataFetcher, context, 2);

            assertAverage(dataFetcher, 1, 0);
        }

        @Test
        @DisplayName("should return average fetch time even if less data of cap")
        void getAverageFetchTimeEvenWhenLessOfSamplesToConsider()
                throws ExecutionException, InterruptedException {
            final var dataFetcher = createFetcher();

            for (int i = 0; i < TIMES_TO_SAMPLE - 1; i++) {
                fetchSample(dataFetcher, context, 2);
            }

            assertAverage(dataFetcher, TIMES_TO_SAMPLE - 1, 0);
        }

        @Test
        @DisplayName("should return the average fetch time if enough data")
        void averageFetchTimeGivenEnoughData() throws ExecutionException, InterruptedException {
            final var dataFetcher = createFetcher();

            for (int i = 0; i < TIMES_TO_SAMPLE; i++) {
                fetchSample(dataFetcher, context, 2);
            }

            assertAverage(dataFetcher, TIMES_TO_SAMPLE, 0);
        }

        @RepeatedTest(10)
        @DisplayName("should average the latest samples and discard older data points")
        void averageFetchTimeButDiscardOldData() throws ExecutionException, InterruptedException {
            final var dataFetcher = createFetcher();

            for (int i = 0; i < TIMES_TO_SAMPLE; i++) {
                fetchSample(dataFetcher, context, 2);
            }
            // This lower bound makes the rolling mean differ from the all-sample mean.
            fetchSample(dataFetcher, context, durations.get(0) * TIMES_TO_SAMPLE + 1);

            assertAverage(dataFetcher, TIMES_TO_SAMPLE + 1, 1);
            assertThat(dataFetcher.getAverageFetchTime())
                    .isNotEqualTo(meanFrom(0));
        }

        private ControlledDataFetcher createFetcher() {
            final var configuration = new DataFetcherConfiguration()
                    .setMetricsTimesToSample(TIMES_TO_SAMPLE);
            configuration.setMetricsAgent(agent);
            doAnswer(invocation -> {
                durations.add(invocation.getArgument(2, Long.class));
                return null;
            })
                    .when(agent)
                    .onSuccess(anyString(), anyString(), anyLong());
            return new ControlledDataFetcher(configuration);
        }

        private void assertAverage(ControlledDataFetcher dataFetcher, int samples, int first) {
            assertThat(durations)
                    .hasSize(samples);
            assertThat(dataFetcher.getAverageFetchTime())
                    .isCloseTo(meanFrom(first), Offset.offset(1e-9));
        }

        private double meanFrom(int first) {
            return durations.subList(first, durations.size()).stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElseThrow();
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

        private final Context context = mock(Context.class);
        private final DataFetcherConfiguration configuration = new DataFetcherConfiguration();

        @Mock
        private MetricsAgent agent;

        @Test
        @DisplayName("should trigger sla failed method")
        void fetchWillFailSla() throws ExecutionException, InterruptedException {
            final var dataFetcher = createFetcher(-1);

            fetchSample(dataFetcher, context, 2);

            final long duration = recordedDuration();
            verify(dataFetcher)
                    .whenFailingSlaFetchTime(anyString(), eq(duration - 1), eq((double) duration));
            verify(agent)
                    .onSlaBreach(anyString(), anyString(), eq(duration - 1), eq((double) duration));
        }

        @Test
        @DisplayName("should not trigger sla failed method as within parameters")
        void fetchWillNotFailSla() throws ExecutionException, InterruptedException {
            final var dataFetcher = createFetcher(1);

            fetchSample(dataFetcher, context, 2);

            assertThat(configuration.getSlaFetchTime())
                    .isEqualTo(recordedDuration() + 1);
            verify(dataFetcher, never())
                    .whenFailingSlaFetchTime(anyString(), anyLong(), anyDouble());
            verify(agent, never())
                    .onSlaBreach(anyString(), anyString(), anyLong(), anyDouble());
        }

        @Test
        @DisplayName("should not trigger sla failed method when the average equals the SLA")
        void fetchAtSlaBoundary() throws ExecutionException, InterruptedException {
            final var dataFetcher = createFetcher(0);

            fetchSample(dataFetcher, context, 2);

            assertThat(configuration.getSlaFetchTime())
                    .isEqualTo(recordedDuration());
            verify(dataFetcher, never())
                    .whenFailingSlaFetchTime(anyString(), anyLong(), anyDouble());
            verify(agent, never())
                    .onSlaBreach(anyString(), anyString(), anyLong(), anyDouble());
        }

        private ControlledDataFetcher createFetcher(long slaOffset) {
            configuration.setMetricsAgent(agent);
            // onSuccess runs before the sample is added and the SLA is checked.
            doAnswer(invocation -> {
                final long duration = invocation.getArgument(2, Long.class);
                configuration.setSlaFetchTime(duration + slaOffset);
                return null;
            })
                    .when(agent)
                    .onSuccess(anyString(), anyString(), anyLong());
            return spy(new ControlledDataFetcher(configuration));
        }

        private long recordedDuration() {
            final var duration = ArgumentCaptor.forClass(Long.class);
            verify(agent)
                    .onSuccess(anyString(), anyString(), duration.capture());
            return duration.getValue();
        }
    }

    private static void fetchSample(
            ControlledDataFetcher dataFetcher, Context context, long minimumDuration)
            throws ExecutionException, InterruptedException {
        final var fetch = dataFetcher.fetch(context);
        dataFetcher.completeAfter(minimumDuration);
        fetch.get();
    }

    private static class ControlledDataFetcher extends DataFetcher<Object, Context> {

        private CompletableFuture<FetchResponse<Object>> response;
        private long responseStart;

        private ControlledDataFetcher(DataFetcherConfiguration configuration) {
            super(configuration, new DataCacheConfiguration()
                    .setExecutor(Runnable::run));
        }

        @Override
        public CompletableFuture<FetchResponse<Object>> fetchResponse(Context context) {
            responseStart = System.currentTimeMillis();
            response = new CompletableFuture<>();
            return response;
        }

        private void completeAfter(long minimumDuration) throws InterruptedException {
            while (System.currentTimeMillis() - responseStart < minimumDuration) {
                TimeUnit.MILLISECONDS.sleep(1);
            }
            response.complete(new FetchResponse<>());
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
                        .succeedsWithin(Duration.ofMillis(1000));
            }

            // This test is used to verify that logging is taking place. No proper assertions are put in place.
            @Test
            @DisplayName("should log metrics on failures with default agent")
            void logInfoMetricsOnFailure() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(20, 50, true);

                assertThat(dataFetcher.fetch(context))
                        .failsWithin(Duration.ofMillis(1000))
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
                        .succeedsWithin(Duration.ofMillis(1000));

                verify(agent).onSuccess(eq("FixedDelayedDataFetcherWithLogging"), anyString(), anyLong());
                verify(agent, never()).onSlaBreach(anyString(), anyString(), anyLong(), anyDouble());
                verify(agent, never()).onFailure(anyString(), anyString(), any(), anyLong());
            }

            @Test
            @DisplayName("should log metrics - onSlaBreach")
            void logInfoMetricsOnSlaBreach() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(agent, 20, 10, false);

                assertThat(dataFetcher.fetch(context))
                        .succeedsWithin(Duration.ofMillis(1000));

                verify(agent).onSuccess(eq("FixedDelayedDataFetcherWithLogging"), anyString(), anyLong());
                verify(agent).onSlaBreach(eq("FixedDelayedDataFetcherWithLogging"), anyString(), eq(10L), anyDouble());
                verify(agent, never()).onFailure(anyString(), anyString(), any(), anyLong());
            }

            @Test
            @DisplayName("should log metrics - onFailure")
            void logInfoMetricsOnFailure() {
                final var dataFetcher = new FixedDelayedDataFetcherWithLogging(agent, 20, 50, true);

                assertThat(dataFetcher.fetch(context))
                        .failsWithin(Duration.ofMillis(1000));

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
                        .failsWithin(Duration.ofMillis(1000)); // CompletableFuture is completed with failure.

                verify(agent, never()).onSuccess(anyString(), anyString(), anyLong());
                verify(agent, never()).onSlaBreach(anyString(), anyString(), anyLong(), anyDouble());
                verify(agent).onFailure(eq("FixedDelayedDataFetcherWithLogging"), anyString(), any(), anyLong());
            }
        }
    }
}
