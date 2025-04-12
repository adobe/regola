/*
 *  Copyright 2025 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola.utils.futures;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FutureUtilsTest {

    @Nested
    class FlatHandle {

        @Test
        void onSuccess() {
            var future = CompletableFuture.completedFuture("success");
            BiFunction<String, Throwable, CompletableFuture<String>> handle =
                    (success, failure) -> CompletableFuture.completedFuture("with " + success);

            var handled = FutureUtils.flatHandle(future, handle);

            assertThat(handled)
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("with success");
        }

        @Test
        void onFailure() {
            CompletableFuture<RuntimeException> future = CompletableFuture.failedFuture(new RuntimeException("failed"));
            BiFunction<RuntimeException, Throwable, CompletableFuture<Object>> handle =
                    (success, failure) -> CompletableFuture.failedFuture(failure);

            var handled = FutureUtils.flatHandle(future, handle);

            assertThat(handled)
                    .failsWithin(Duration.ofMillis(50))
                    .withThrowableOfType(ExecutionException.class)
                    .withRootCauseInstanceOf(RuntimeException.class)
                    .withMessageContaining("failed");
        }
    }

    @Nested
    class SupplyAsync {

        @Test
        void withExecutor() {
            var future = FutureUtils.supplyAsync(() -> "result", Executors.newSingleThreadExecutor());

            assertThat(future)
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("result");
        }

        @Test
        void withoutExecutor() {
            var future = FutureUtils.supplyAsync(() -> "result", null);

            assertThat(future)
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo("result");
        }

        @Test
        void withException() {
            var future = FutureUtils.supplyAsync(() -> {throw new RuntimeException("failure");}, null);

            assertThatThrownBy(future::join)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("failure");
        }

        @Test
        void runsInProvidedExecutor() {
            AtomicBoolean executedInCustomExecutor = new AtomicBoolean(false);
            Executor executor = runnable -> {
                executedInCustomExecutor.set(true);
                new Thread(runnable).start();
            };

            FutureUtils.supplyAsync(() -> "result", executor).join();

            assertThat(executedInCustomExecutor)
                    .isTrue();
        }
    }

    @Nested
    class Sequence {

        @Test
        void withEmptyList() {
            var result = FutureUtils.sequence(List.of());

            assertThat(result.toCompletableFuture())
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo(List.of());
        }

        @Test
        void withAllSuccessfulFutures() {
            var futures = List.of(
                    CompletableFuture.completedFuture("first"),
                    CompletableFuture.completedFuture("second"),
                    CompletableFuture.completedFuture("third")
            );

            var result = FutureUtils.sequence(futures);

            assertThat(result.toCompletableFuture())
                    .succeedsWithin(Duration.ofMillis(50))
                    .isEqualTo(List.of("first", "second", "third"));
        }

        @Test
        void withOneFailedFuture() {
            List<CompletableFuture<String>> futures = List.of(
                    CompletableFuture.completedFuture("first"),
                    CompletableFuture.failedFuture(new RuntimeException("failure")),
                    CompletableFuture.completedFuture("third")
            );

            var result = FutureUtils.sequence(futures);

            assertThatThrownBy(result.toCompletableFuture()::join)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("failure");
        }

        @Test
        void withMixedFutures() {
            var futures = List.of(
                    CompletableFuture.completedFuture("first"),
                    CompletableFuture.supplyAsync(() -> "second"),
                    CompletableFuture.completedFuture("third")
            );

            var result = FutureUtils.sequence(futures);

            assertThat(result.toCompletableFuture())
                    .succeedsWithin(Duration.ofMillis(100))
                    .isEqualTo(List.of("first", "second", "third"));
        }
    }
}
