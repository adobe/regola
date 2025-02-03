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
import com.adobe.abp.regola.datafetchers.cache.DataCacheConfiguration;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CaffeineCacheTest {

    @Mock
    Predicate<String> testPredicate;

    @Test
    @DisplayName("should supply cache value when getting element for the first time")
    void supplyCacheOnGetting() {
        CaffeineCache<Object> cache = new CaffeineCache<>(new DataCacheConfiguration());

        final var cached = cache.get("foo", (k) -> CompletableFuture.supplyAsync(() -> k + "-async"));

        assertThat(cached)
                .succeedsWithin(Duration.ofMillis(10))
                .isEqualTo("foo-async");
    }

    @Test
    @DisplayName("should get from value cache once stored already")
    void getFromCacheOnceStored() {
        CaffeineCache<Object> cache = new CaffeineCache<>(new DataCacheConfiguration());
        verifyNoInteractions(testPredicate);

        cache.get("foo", (k) -> {
            testPredicate.test(k);
            return CompletableFuture.supplyAsync(() -> k + "-async");
        });

        verify(testPredicate).test("foo"); // called once

        final var cached = cache.get("foo", (k) -> {
            testPredicate.test(k);
            return CompletableFuture.supplyAsync(() -> k + "-async");
        });
        assertThat(cached)
                .succeedsWithin(Duration.ofMillis(10))
                .isEqualTo("foo-async");

        verify(testPredicate).test("foo"); // still called once
    }

    @Test
    @DisplayName("should handle failures")
    void handlingFailures() {
        CaffeineCache<Object> cache = new CaffeineCache<>(new DataCacheConfiguration());

        final var cached = cache.get("foo", (k) -> CompletableFuture.failedFuture(new RuntimeException("Failing in test")));

        assertThat(cached)
                .failsWithin(Duration.ofMillis(10))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("should work with custom executor")
    void getFromCacheWithCustomExecutor() {
        DataCacheConfiguration configuration = new DataCacheConfiguration()
                .setExecutor(Executors.newCachedThreadPool());
        CaffeineCache<Object> cache = new CaffeineCache<>(configuration);
        cache.get("foo", (k) -> CompletableFuture.supplyAsync(() -> k + "-async"));

        final var cached = cache.get("foo", (k) -> CompletableFuture.supplyAsync(() -> k + "-async"));
        assertThat(cached)
                .succeedsWithin(Duration.ofMillis(10))
                .isEqualTo("foo-async");
    }
}
