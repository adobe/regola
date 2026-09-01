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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CaffeineCacheTest {

    @Test
    @DisplayName("should supply cache value when getting element for the first time")
    void returnsMappedValueOnCacheMiss() {
        CaffeineCache<Object> cache = new CaffeineCache<>(new DataCacheConfiguration());

        final var cached = cache.get("foo", (k) -> CompletableFuture.completedFuture(k + "-async"));

        assertThat(cached)
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-async");
    }

    @Test
    @DisplayName("should get from value cache once stored already")
    void reusesCachedValueWithoutRemapping() {
        CaffeineCache<Object> cache = new CaffeineCache<>(new DataCacheConfiguration());
        AtomicInteger mappingInvocations = new AtomicInteger();

        cache.get("foo", (k) -> {
            mappingInvocations.incrementAndGet();
            return CompletableFuture.completedFuture(k + "-async");
        });

        assertThat(mappingInvocations).hasValue(1);

        final var cached = cache.get("foo", (k) -> {
            mappingInvocations.incrementAndGet();
            return CompletableFuture.completedFuture(k + "-async");
        });
        assertThat(cached)
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-async");

        assertThat(mappingInvocations).hasValue(1);
    }

    @Test
    @DisplayName("should return promptly while the mapped value is incomplete")
    void returnPromptlyForIncompleteMappingFuture() {
        CaffeineCache<String> cache = new CaffeineCache<>(new DataCacheConfiguration());
        CompletableFuture<String> mappingFuture = new CompletableFuture<>();

        CompletableFuture<String> cached = assertTimeoutPreemptively(
                Duration.ofSeconds(1), () -> cache.get("foo", key -> mappingFuture));

        assertThat(cached).isNotDone();

        mappingFuture.complete("foo-async");
        assertThat(cached)
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-async");
    }

    @Test
    @DisplayName("should share an in-flight load between concurrent callers")
    void singleFlightForConcurrentCallers() throws Exception {
        CaffeineCache<String> cache = new CaffeineCache<>(new DataCacheConfiguration());
        CompletableFuture<String> mappingFuture = new CompletableFuture<>();
        AtomicInteger mappingInvocations = new AtomicInteger();
        Function<String, CompletableFuture<String>> mappingFunction = key -> {
            mappingInvocations.incrementAndGet();
            return mappingFuture;
        };
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        final var callers = Executors.newFixedThreadPool(2);

        try {
            final var firstCaller = callers.submit(() -> {
                callersReady.countDown();
                start.await();
                return cache.get("foo", mappingFunction);
            });
            final var secondCaller = callers.submit(() -> {
                callersReady.countDown();
                start.await();
                return cache.get("foo", mappingFunction);
            });

            assertThat(callersReady.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            CompletableFuture<String> first = firstCaller.get(1, TimeUnit.SECONDS);
            CompletableFuture<String> second = secondCaller.get(1, TimeUnit.SECONDS);

            assertThat(mappingInvocations).hasValue(1);
            assertThat(first).isSameAs(second);

            mappingFuture.complete("foo-async");
            assertThat(first)
                    .succeedsWithin(Duration.ofSeconds(1))
                    .isEqualTo("foo-async");
            assertThat(second)
                    .succeedsWithin(Duration.ofSeconds(1))
                    .isEqualTo("foo-async");
        } finally {
            start.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    @DisplayName("should propagate exceptional completion")
    void propagateExceptionalCompletion() {
        CaffeineCache<Object> cache = new CaffeineCache<>(new DataCacheConfiguration());
        RuntimeException failure = new RuntimeException("Failing in test");

        final var cached = cache.get("foo", (k) -> CompletableFuture.failedFuture(failure));

        assertThat(cached)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCause(failure);
    }

    @Test
    @DisplayName("should retry a load once Caffeine evicts an exceptional completion")
    void retryAfterExceptionalCompletionIsEvicted() throws InterruptedException {
        CaffeineCache<String> cache = new CaffeineCache<>(new DataCacheConfiguration());
        AtomicInteger mappingInvocations = new AtomicInteger();
        RuntimeException failure = new RuntimeException("Failing in test");

        final var failed = cache.get("foo", key -> {
            mappingInvocations.incrementAndGet();
            return CompletableFuture.failedFuture(failure);
        });
        assertThat(failed)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCause(failure);

        CompletableFuture<String> retried;
        final long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        do {
            retried = cache.get("foo", key -> {
                mappingInvocations.incrementAndGet();
                return CompletableFuture.completedFuture(key + "-recovered");
            });
            if (mappingInvocations.get() == 1) {
                Thread.sleep(10);
            }
        } while (mappingInvocations.get() == 1 && System.nanoTime() < deadlineNanos);

        assertThat(retried)
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-recovered");
        assertThat(mappingInvocations).hasValue(2);
    }

    @Test
    @DisplayName("should invoke mapping function on Caffeine's default executor when none is configured")
    void invokesMappingFunctionOnDefaultExecutor() {
        CaffeineCache<Thread> cache = new CaffeineCache<>(new DataCacheConfiguration());

        final var cached = cache.get("foo", key -> CompletableFuture.completedFuture(Thread.currentThread()));

        assertThat(cached)
                .succeedsWithin(Duration.ofSeconds(1))
                .isInstanceOf(ForkJoinWorkerThread.class);
    }

    @Test
    @DisplayName("should work with custom executor")
    void invokesMappingFunctionOnConfiguredExecutor() {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
                new Thread(runnable, "caffeine-cache-test-executor"));
        DataCacheConfiguration configuration = new DataCacheConfiguration()
                .setExecutor(executor);
        CaffeineCache<Object> cache = new CaffeineCache<>(configuration);

        try {
            final var cached = cache.get("foo", key -> CompletableFuture.completedFuture(
                    Thread.currentThread().getName()));

            assertThat(cached)
                    .succeedsWithin(Duration.ofSeconds(1))
                    .isEqualTo("caffeine-cache-test-executor");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("should honor configured maximum cache size")
    void honorMaximumCacheSize() {
        DataCacheConfiguration configuration = new DataCacheConfiguration()
                .setCacheSize(0)
                .setExecutor(Runnable::run);
        CaffeineCache<String> cache = new CaffeineCache<>(configuration);
        AtomicInteger mappingInvocations = new AtomicInteger();
        Function<String, CompletableFuture<String>> mappingFunction = key ->
                CompletableFuture.completedFuture(key + "-" + mappingInvocations.incrementAndGet());

        assertThat(cache.get("foo", mappingFunction))
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-1");
        assertThat(cache.get("foo", mappingFunction))
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-2");
    }

    @Test
    @DisplayName("should honor configured expiry")
    void honorExpiry() {
        DataCacheConfiguration configuration = new DataCacheConfiguration()
                .setCacheExpireAfterMinutes(0)
                .setExecutor(Runnable::run);
        CaffeineCache<String> cache = new CaffeineCache<>(configuration);
        AtomicInteger mappingInvocations = new AtomicInteger();
        Function<String, CompletableFuture<String>> mappingFunction = key ->
                CompletableFuture.completedFuture(key + "-" + mappingInvocations.incrementAndGet());

        assertThat(cache.get("foo", mappingFunction))
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-1");
        assertThat(cache.get("foo", mappingFunction))
                .succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo("foo-2");
    }
}
