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

package com.adobe.abp.regola.utils.futures;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class FutureUtils {

    private FutureUtils() {
        throw new IllegalAccessError("Do not instantiate");
    }

    private static class Try<S, E extends Throwable> {
        private final S success;
        private final E failure;
        private Try(S success, E failure) {
            this.success = success;
            this.failure = failure;
        }
    }

    public static <T, S> CompletableFuture<S> flatHandle(CompletableFuture<T> future, BiFunction<? super T, Throwable, CompletableFuture<S>> handle) {
        return future.handle(Try::new)
                .thenCompose(t -> handle.apply(t.success, t.failure));
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed by a task running in the given executor
     * with the value obtained by calling the given Supplier.
     * Optionally use the provided executor if not null.
     *
     * @param supplier to be executed by the completable future and which will return the wanted object of type U
     * @param executor used by the completable future. If null, the default executor will be used.
     * @param <U> type of the data to return
     * @return a Completable Future
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return Optional.ofNullable(executor)
                .map(e -> CompletableFuture.supplyAsync(supplier, e))
                .orElseGet(() -> CompletableFuture.supplyAsync(supplier));
    }
}
