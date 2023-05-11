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

import com.adobe.abp.regola.datafetchers.cache.CaffeineCache;
import com.adobe.abp.regola.datafetchers.cache.DataCache;
import com.adobe.abp.regola.datafetchers.cache.DataCacheConfiguration;
import com.adobe.abp.regola.utils.futures.FutureUtils;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Class defining how data is fetched from a given data source, as identified by {@link com.adobe.abp.regola.facts.DataSource}
 *
 * @param <D> type of the data
 * @param <C> context used when fetching the data. C must implement {@link Context}
 *
 * All data fetchers must implement the {@link #fetchResponse(Context)} method.
 *
 * This data fetcher allows for data to be cached.
 * This abstract data fetcher will be responsible for collecting metrics about the data fetching.
 */
public abstract class DataFetcher<D, C extends Context> {

    private final DataFetcherConfiguration configuration;
    private final DescriptiveStatistics descriptiveStatistics;
    private final DataCache<D> cache;

    /**
     * Construct a data fetcher with a custom configuration and a custom cache
     *
     * @param configuration used to build this data fetcher
     * @param cache used to cache results from this data fetcher
     */
    public DataFetcher(DataFetcherConfiguration configuration, DataCache<D> cache) {
        this.configuration = configuration;
        this.descriptiveStatistics = new DescriptiveStatistics(configuration.getMetricsTimesToSample());
        this.cache = cache;
    }

    /**
     * Construct a data fetcher with a custom configuration and the default caffeine-based cache with some custom configuration
     *
     * @param configuration used to build this data fetcher
     * @param cacheConfiguration used to build the caffeine cache used by this data fetcher
     */
    public DataFetcher(DataFetcherConfiguration configuration, DataCacheConfiguration cacheConfiguration) {
        this(configuration, new CaffeineCache<>(cacheConfiguration));
    }

    /**
     * Construct a data fetcher with a custom configuration and the default caffeine-based cache
     *
     * @param configuration used to build this data fetcher
     */
    public DataFetcher(DataFetcherConfiguration configuration) {
        this(configuration, new CaffeineCache<>(new DataCacheConfiguration()));
    }

    /**
     * Construct a data fetcher with the default configuration and a custom cache
     *
     * @param cache used to cache results from this data fetcher
     */
    public DataFetcher(DataCache<D> cache) {
        this(new DataFetcherConfiguration(), cache);
    }

    /**
     * Construct a data fetcher with the default configuration and the default caffeine-based cache
     */
    public DataFetcher() {
        this(new DataFetcherConfiguration(), new DataCacheConfiguration());
    }

    /**
     * Fetch data from a data source
     *
     * @param context used to fetch the data
     * @return some optional data of type D
     */
    public abstract CompletableFuture<FetchResponse<D>> fetchResponse(C context);

    /**
     * Key uniquely identifying the fetch request for the context.
     * This will allow the data fetcher to cache requests if possible.
     *
     * By default the key returned by this method is a random string, so to enable caching this method needs to be overridden.
     *
     * @param context for which to calculate the request key
     * @return the request key
     */
    public String calculateRequestKey(C context) {
        return String.format("%s-%s", RandomStringUtils.randomAlphabetic(16), Objects.hash(context));
    }

    /**
     * Fetch data for the given context
     *
     * @param context used to fetch the data
     * @return completable future of data of type D
     */
    public CompletableFuture<D> fetch(C context) {
        final var requestKey = calculateRequestKey(context);
        return cache.get(requestKey, _key -> fetchWithMetrics(context, requestKey).thenApply(FetchResponse::getData));
    }

    /**
     * Fetch data for the given context while collecting metrics about it.
     *
     * Metrics collected:
     * - time to fetch data in milliseconds
     *
     * Metrics are stored within this data fetcher.
     *
     * @param context used to fetch the data
     * @param requestKey used when logging request info
     * @return completable future fetch response with data of type D
     */
    private CompletableFuture<FetchResponse<D>> fetchWithMetrics(C context, String requestKey) {
        final long start = System.currentTimeMillis();

        return FutureUtils.flatHandle(fetchResponse(context), (response, throwable) -> {
            final var requestTime = System.currentTimeMillis() - start;
            if (response != null) {
                configuration.getMetricsAgent().onSuccess(this.getClass().getSimpleName(), requestKey, requestTime);
                descriptiveStatistics.addValue(requestTime);
                final var averageFetchTime = getAverageFetchTime();
                final var sla = configuration.getSlaFetchTime();
                if (sla > 0 && averageFetchTime > sla) {
                    // We use the average fetch time when checking the SLA to avoid anomalies to raise issues.
                    configuration.getMetricsAgent().onSlaBreach(this.getClass().getSimpleName(), requestKey, sla, requestTime);
                    whenFailingSlaFetchTime(requestKey, sla, averageFetchTime);
                }

                return CompletableFuture.completedFuture(response);
            }

            configuration.getMetricsAgent().onFailure(this.getClass().getSimpleName(), requestKey, throwable, requestTime);

            return CompletableFuture.failedFuture(throwable);
        });
    }

    /**
     * Evaluate the average time to fetch data from this data fetcher.
     *
     * @return average time to fetch data. 0 is returned if no enough data has been collected.
     */
    public double getAverageFetchTime() {
        return descriptiveStatistics.getMean();
    }

    /**
     * Override this method to get a handler on SLA breaching.
     *
     * @param requestKey for the request
     * @param slaFetchTime agreed time
     * @param requestTime actual observed time
     */
    public void whenFailingSlaFetchTime(String requestKey, long slaFetchTime, double requestTime) {
        // do nothing
    }

    @Override
    public String toString() {
        return "DataFetcher{" +
                "configuration=" + configuration +
                ", times=" + Arrays.toString(descriptiveStatistics.getValues()) +
                ", avgTime=" + getAverageFetchTime() +
                '}';
    }
}
