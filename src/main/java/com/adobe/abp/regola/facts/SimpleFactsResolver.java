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
import com.adobe.abp.regola.utils.futures.FutureUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class SimpleFactsResolver<C extends Context> implements FactsResolver {

    private final C context;
    private final Map<String, DataSource> dataSourcesMap = new LinkedHashMap<>();
    private final Map<DataSource, DataFetcher<?, C>> dataFetchers;
    private final transient Map<String, Function<Object, Object>> factFetchers = new LinkedHashMap<>();
    private final Executor executor;

    public SimpleFactsResolver(C context, Map<DataSource, DataFetcher<?, C>> dataFetchers) {
        this(context, dataFetchers, null);
    }

    public SimpleFactsResolver(C context, Map<DataSource, DataFetcher<?, C>> dataFetchers, Executor executor) {
        this.context = context;
        this.dataFetchers = dataFetchers;
        this.executor = executor;
    }

    public SimpleFactsResolver() {
        this(null, Map.of());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D, F> void addFact(Fact<D, F> fact) {
        dataSourcesMap.put(fact.getKey(), fact.getDataSource());
        factFetchers.put(fact.getKey(), (Function<Object, Object>) fact.getFactFetcher());
    }

    @Override
    public CompletableFuture<Object> resolveFact(String key) {
        final var dataSource = dataSourcesMap.getOrDefault(key, StandardDataSources.NONE);
        final var dataFetcher = dataFetchers.get(dataSource);
        if (dataFetcher != null) {
            return dataFetcher.fetch(context)
                    .thenApply(data -> factFetchers.get(key).apply(data));
        } else {
            // If we cannot find a data fetcher, then attempt to solve the Fact using only the factFetcher with a null data object
            // Note that this may result in a NPE if the configured factFetcher for the 'key' is expecting a non-null data object.
            return FutureUtils.supplyAsync(() ->
                    Optional.ofNullable(factFetchers.get(key))
                            .map(fetcher -> fetcher.apply(null))
                            .orElse(null), executor);
        }
    }
}
