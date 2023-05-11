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

package com.adobe.abp.regola.datafetchers.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CaffeineCache<V> implements DataCache<V> {

    private static final Logger LOG = Logger.getLogger(CaffeineCache.class.getName());

    private final transient AsyncCache<String, CompletableFuture<V>> cache;

    public CaffeineCache(DataCacheConfiguration configuration) {
        final var builder = Caffeine.newBuilder()
                .expireAfterWrite(configuration.getCacheExpireAfterMinutes(), TimeUnit.MINUTES)
                .maximumSize(configuration.getCacheSize());

        Optional.ofNullable(configuration.getExecutor())
                .ifPresent(builder::executor);

        this.cache = builder.buildAsync();
    }

    @Override
    public CompletableFuture<V> get(String key, Function<String, CompletableFuture<V>> mappingFunction) {
        try {
            return cache.get(key, mappingFunction).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.log(Level.SEVERE, String.format("Data for requestKey=%s could not be fetched due to exception=%s",
                    key, e.getMessage()));
            return CompletableFuture.failedFuture(e);
        }
    }
}
