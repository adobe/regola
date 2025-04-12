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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    /**
     * Transforms the result or exception of a given {@link CompletableFuture} into another {@link CompletableFuture}
     * using the provided {@link BiFunction}.
     *
     * <p>This method allows handling both the success and failure cases of the input future and mapping them
     * to a new {@link CompletableFuture} of a potentially different type.</p>
     *
     * @param <T> the type of the result of the input {@link CompletableFuture}
     * @param <S> the type of the result of the output {@link CompletableFuture}
     * @param future the input {@link CompletableFuture} to handle
     * @param handle a {@link BiFunction} that takes the result (or null in case of failure) and the exception (or null in case of success)
     *               and returns a new {@link CompletableFuture} of type {@code S}
     * @return a {@link CompletableFuture} of type {@code S} resulting from applying the {@code handle} function
     *         to the result or exception of the input {@code future}
     * @throws NullPointerException if {@code future} or {@code handle} is null
     */
    public static <T, S> CompletableFuture<S> flatHandle(
            CompletableFuture<T> future,
            BiFunction<? super T, Throwable, CompletableFuture<S>> handle) {
        return future.handle(Try::new)
                .thenCompose(t -> handle.apply(t.success, t.failure));
    }

    /**
     * Creates a new {@link CompletableFuture} that is asynchronously completed by executing the given {@link Supplier}
     * in the provided {@link Executor}. If the executor is {@code null}, the default executor is used.
     *
     * <p>This method allows for flexible execution of asynchronous tasks, either using a custom executor or the default one.</p>
     *
     * @param <U> the type of the result produced by the {@link Supplier}
     * @param supplier the {@link Supplier} whose result will complete the {@link CompletableFuture}
     * @param executor the {@link Executor} to run the task. If {@code null}, the default executor will be used.
     * @return a {@link CompletableFuture} that will be completed with the result of the {@link Supplier}
     * @throws NullPointerException if the {@code supplier} is {@code null}
     */
    public static <U> CompletableFuture<U> supplyAsync(
            Supplier<U> supplier,
            Executor executor) {
        return Optional.ofNullable(executor)
                .map(e -> CompletableFuture.supplyAsync(supplier, e))
                .orElseGet(() -> CompletableFuture.supplyAsync(supplier));
    }

    /**
     * The sequence function swaps the execution context of an applicative of a monad into a monad of an applicative.
     * More about this here: <a href="https://livebook.manning.com/book/functional-programming-in-scala/chapter-12/3">Functional Programming in Scala</a>
     * <p>
     * In this particular case, we instruct for the futures provided to be executed in parallel with the use of CompletableFuture.allOf().
     * The execution itself, however, is handled to the caller as this function returns a CompletionStage.
     * <p>
     * Special credits to @fburato for this piece of work.
     *
     * @param futures to be sequenced
     * @param <T> type of the data list
     * @param <E> future type of the provided list
     * @return a CompletionStage of a list of type T
     */
    public static <T, E extends CompletionStage<T>> CompletionStage<List<T>> sequence(final List<E> futures) {
        final CompletableFuture<Void> allFuturesResult =
                CompletableFuture.allOf(futures.stream()
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new));
        return allFuturesResult.thenApply(neverUsedVoid -> futures.stream()
                .map(stage -> stage.toCompletableFuture().join())
                .collect(Collectors.toList()));
    }
}
