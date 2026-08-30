/*
 *  Copyright 2026 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License
 */

package com.adobe.abp.regola.rules;

import com.adobe.abp.regola.results.Result;
import com.adobe.abp.regola.results.RuleResult;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testing LockingEvaluationResult")
class LockingEvaluationResultTest {

    // ── Minimal stub subclass used across tests ───────────────────────────────────────────────────

    /**
     * Concrete stub that owns a {@code String} label to exercise the copy-under-lock pattern in
     * {@link #snapshot()}. The label is set inside {@link #startEvaluation()} to verify that
     * publication-under-lock works correctly.
     */
    static class StubResult extends LockingEvaluationResult {

        // Subclass-owned mutable state — written on the status()-calling thread inside
        // startEvaluation(), read under readLocked() in snapshot().
        private String label = "initial";
        private final AtomicInteger afterCompletionCount = new AtomicInteger();
        private final AtomicReference<Result> afterCompletionResult = new AtomicReference<>();
        private final AtomicReference<Throwable> afterCompletionThrowable = new AtomicReference<>();

        // Externally injectable start logic — lets each test drive startEvaluation differently
        private Runnable startBehavior = () -> {};

        void setStartBehavior(Runnable startBehavior) {
            this.startBehavior = startBehavior;
        }

        @Override
        protected void startEvaluation() {
            startBehavior.run();
        }

        @Override
        protected void afterCompletion(Result completedResult, Throwable throwable) {
            afterCompletionCount.incrementAndGet();
            afterCompletionResult.set(completedResult);
            afterCompletionThrowable.set(throwable);
        }

        @Override
        public RuleResult snapshot() {
            final String capturedLabel = readLocked(() -> label);
            final Result capturedResult = getResult();
            final RuleResult r = new RuleResult();
            r.setResult(capturedResult);
            r.setType("STUB");
            r.setMessage(capturedLabel);
            return r;
        }

        // Exposed so tests can drive decideUnderLock from outside
        void decide(Supplier<Optional<Result>> decision) {
            decideUnderLock(decision);
        }

        void completeWith(Result r) {
            complete(r);
        }

        void completeWithException(Throwable t) {
            completeExceptionally(t);
        }

        void setLabel(String value) {
            this.label = value;
        }
    }

    private Supplier<Optional<Result>> returning(Result r) {
        return () -> Optional.of(r);
    }

    private Supplier<Optional<Result>> pending() {
        return Optional::empty;
    }

    // ── One-time startup ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("one-time startup")
    class OneTimeStartup {

        @Test
        @DisplayName("status() returns the same CompletableFuture on repeated calls")
        void repeatedStatusReturnsSameFuture() {
            final var stub = new StubResult();
            stub.setStartBehavior(() -> stub.completeWith(Result.VALID));

            final var first = stub.status();
            final var second = stub.status();
            final var third = stub.status();

            assertThat(first).isSameAs(second);
            assertThat(first).isSameAs(third);
        }

        @Test
        @DisplayName("startEvaluation is invoked exactly once across repeated status() calls")
        void startEvaluationCalledOnce() {
            final var callCount = new AtomicInteger();
            final var stub = new StubResult();
            stub.setStartBehavior(() -> {
                callCount.incrementAndGet();
                stub.completeWith(Result.VALID);
            });

            stub.status();
            stub.status();
            stub.status();

            assertThat(callCount.get()).isEqualTo(1);
        }
    }

    // ── decideUnderLock mutual exclusion ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("decideUnderLock")
    class DecideUnderLock {

        @Test
        @DisplayName("pending decision does not complete the future")
        void pendingDecisionDoesNotComplete() {
            final var stub = new StubResult();
            stub.status(); // start (no-op startBehavior)

            stub.decide(pending());

            assertThat(stub.isDone()).isFalse();
        }

        @Test
        @DisplayName("terminal decision completes the future with the given result")
        void terminalDecisionCompletesFuture() {
            final var stub = new StubResult();
            stub.status();

            stub.decide(returning(Result.VALID));

            assertThat(stub.status().join()).isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("second terminal decision after first is already done is ignored")
        void secondDecisionIgnoredAfterCompletion() {
            final var stub = new StubResult();
            stub.status();

            stub.decide(returning(Result.VALID));
            stub.decide(returning(Result.INVALID));

            assertThat(stub.status().join()).isEqualTo(Result.VALID);
        }

        /**
         * Torn-state regression: spin up COMPETITOR_COUNT threads, each racing to settle the future
         * with a *different* terminal Result. After all threads finish, the snapshot result must
         * equal whatever result the future settled to — they must never diverge.
         */
        @RepeatedTest(200)
        @DisplayName("concurrent competing decisions never produce a torn terminal state")
        void concurrentDecisionsNoTornState() throws InterruptedException {
            final int competitorCount = 4;
            final var stub = new StubResult();
            stub.status(); // start (no-op)

            final Result[] candidates = {Result.VALID, Result.INVALID, Result.FAILED, Result.OPERATION_NOT_SUPPORTED};
            final var barrier = new CyclicBarrier(competitorCount);
            final var executor = Executors.newFixedThreadPool(competitorCount);

            for (int i = 0; i < competitorCount; i++) {
                final Result candidate = candidates[i];
                executor.submit(() -> {
                    try {
                        barrier.await(); // synchronize all threads to maximize contention
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    stub.decide(returning(candidate));
                });
            }

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            final Result futureResult = stub.status().join();
            final Result snapshotResult = stub.snapshot().getResult();

            assertThat(snapshotResult)
                    .as("snapshot result must equal future result — torn state detected")
                    .isEqualTo(futureResult);
        }
    }

    // ── complete() ───────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("complete()")
    class CompleteMethod {

        @Test
        @DisplayName("completes future with given result")
        void completesFuture() {
            final var stub = new StubResult();
            stub.status();

            stub.completeWith(Result.VALID);

            assertThat(stub.status().join()).isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("is idempotent — second call does not change the settled result")
        void idempotent() {
            final var stub = new StubResult();
            stub.status();

            stub.completeWith(Result.VALID);
            stub.completeWith(Result.INVALID);

            assertThat(stub.status().join()).isEqualTo(Result.VALID);
        }
    }

    // ── completeExceptionally() ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeExceptionally()")
    class CompleteExceptionally {

        @Test
        @DisplayName("always sets Result.FAILED before transitioning the future")
        void alwaysSetsFailed() {
            final var stub = new StubResult();
            stub.status();

            stub.completeWithException(new RuntimeException("boom"));

            assertThat(stub.snapshot().getResult()).isEqualTo(Result.FAILED);
        }

        @Test
        @DisplayName("future is completed exceptionally")
        void futureCompletedExceptionally() {
            final var stub = new StubResult();
            stub.status();

            stub.completeWithException(new RuntimeException("boom"));

            assertThat(stub.status()).isCompletedExceptionally();
        }

        @Test
        @DisplayName("snapshot reflects FAILED before the future's whenComplete callback fires")
        void snapshotReflectsFailedAtCallbackTime() {
            final var stub = new StubResult();
            final AtomicReference<Result> resultAtCallback = new AtomicReference<>();

            stub.status().whenComplete((r, t) -> resultAtCallback.set(stub.snapshot().getResult()));
            stub.completeWithException(new RuntimeException("test"));

            // status().join() would throw; just check the callback observed FAILED
            assertThat(resultAtCallback.get()).isEqualTo(Result.FAILED);
        }
    }

    // ── readLocked() / snapshot copy-under-lock ───────────────────────────────────────────────────

    @Nested
    @DisplayName("readLocked()")
    class ReadLocked {

        @Test
        @DisplayName("snapshot copies subclass-owned state under the lock")
        void snapshotCopiesStateUnderLock() {
            final var stub = new StubResult();
            stub.setStartBehavior(() -> {
                stub.setLabel("evaluation-started");
                stub.completeWith(Result.VALID);
            });

            stub.status().join();

            assertThat(stub.snapshot().getMessage()).isEqualTo("evaluation-started");
        }
    }

    // ── afterCompletion dispatch ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("afterCompletion")
    class AfterCompletion {

        @Test
        @DisplayName("fires exactly once on normal completion")
        void firesOnceNormalCompletion() {
            final var stub = new StubResult();
            stub.status();

            stub.completeWith(Result.VALID);

            assertThat(stub.afterCompletionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("fires exactly once on exceptional completion")
        void firesOnceExceptionalCompletion() {
            final var stub = new StubResult();
            stub.status();

            stub.completeWithException(new RuntimeException());

            assertThat(stub.afterCompletionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("does not fire a second time when status() is called again after completion")
        void doesNotFireAgainOnRepeatedStatus() {
            final var stub = new StubResult();
            stub.status();
            stub.completeWith(Result.VALID);

            stub.status();
            stub.status();

            assertThat(stub.afterCompletionCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("receives the terminal result on normal completion")
        void receivesTerminalResult() {
            final var stub = new StubResult();
            stub.status();
            stub.completeWith(Result.INVALID);

            assertThat(stub.afterCompletionResult.get()).isEqualTo(Result.INVALID);
        }

        @Test
        @DisplayName("receives FAILED and throwable on exceptional completion")
        void receivesFailedAndThrowable() {
            final var stub = new StubResult();
            final var cause = new RuntimeException("test");
            stub.status();

            stub.completeWithException(cause);

            assertThat(stub.afterCompletionResult.get()).isEqualTo(Result.FAILED);
            assertThat(stub.afterCompletionThrowable.get()).isSameAs(cause);
        }

        @Test
        @DisplayName("completes exceptionally when afterCompletion throws after normal completion")
        void callbackFailureSettlesNormalCompletion() {
            final var callbackFailure = new IllegalStateException("callback failed");
            final var stub = new StubResult() {
                @Override
                protected void afterCompletion(Result completedResult, Throwable throwable) {
                    super.afterCompletion(completedResult, throwable);
                    throw callbackFailure;
                }
            };

            stub.status();
            stub.completeWith(Result.VALID);

            assertThat(stub.status()).isCompletedExceptionally();
            assertThatThrownBy(() -> stub.status().join())
                    .hasCause(callbackFailure);
            assertThat(stub.snapshot().getResult()).isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("preserves the original failure when afterCompletion also throws")
        void callbackFailureDoesNotReplaceExceptionalCompletion() {
            final var cause = new RuntimeException("evaluation failed");
            final var callbackFailure = new IllegalStateException("callback failed");
            final var stub = new StubResult() {
                @Override
                protected void afterCompletion(Result completedResult, Throwable throwable) {
                    super.afterCompletion(completedResult, throwable);
                    throw callbackFailure;
                }
            };

            stub.status();
            stub.completeWithException(cause);

            assertThat(stub.status()).isCompletedExceptionally();
            assertThatThrownBy(() -> stub.status().join())
                    .hasCause(cause);
            assertThat(cause.getSuppressed()).containsExactly(callbackFailure);
            assertThat(stub.snapshot().getResult()).isEqualTo(Result.FAILED);
        }

        @Test
        @DisplayName("completes exceptionally when afterCompletion rethrows the original failure")
        void callbackRethrowsOriginalFailure() {
            final var cause = new RuntimeException("evaluation failed");
            final var stub = new StubResult() {
                @Override
                protected void afterCompletion(Result completedResult, Throwable throwable) {
                    super.afterCompletion(completedResult, throwable);
                    throw (RuntimeException) throwable;
                }
            };

            stub.status();
            stub.completeWithException(cause);

            assertThat(stub.status()).isCompletedExceptionally();
            assertThatThrownBy(() -> stub.status().join())
                    .hasCause(cause);
            assertThat(cause.getSuppressed()).isEmpty();
            assertThat(stub.snapshot().getResult()).isEqualTo(Result.FAILED);
        }

        @Test
        @DisplayName("does not hold the internal lock when afterCompletion is called")
        void notCalledUnderLock() throws InterruptedException {
            // We verify that another thread can call readLocked() while afterCompletion is running.
            // If afterCompletion were called under the lock, the readLocked() call below would deadlock
            // (ReentrantLock is reentrant for the *same* thread, but not for other threads).
            final var lockAccessibleDuringCallback = new CountDownLatch(1);
            final var stub = new StubResult() {
                @Override
                protected void afterCompletion(Result completedResult, Throwable throwable) {
                    super.afterCompletion(completedResult, throwable);
                    // Spawn a separate thread that tries to acquire the lock via readLocked.
                    // If the lock were held here, this would block.
                    final var future = CompletableFuture.runAsync(() ->
                            readLocked(() -> {
                                lockAccessibleDuringCallback.countDown();
                                return null;
                            })
                    );
                    try {
                        future.get(2, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // Do not decrement latch — test will fail on assertion below
                    }
                }
            };

            stub.status();
            stub.completeWith(Result.VALID);

            assertThat(lockAccessibleDuringCallback.await(3, TimeUnit.SECONDS))
                    .as("lock must not be held during afterCompletion — another thread could not acquire it")
                    .isTrue();
        }
    }

    // ── startEvaluation() failure ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startEvaluation failure")
    class StartEvaluationFailure {

        @Test
        @DisplayName("future is completed exceptionally if startEvaluation throws")
        void futureCompletedWhenStartThrows() {
            final var stub = new StubResult();
            stub.setStartBehavior(() -> { throw new IllegalStateException("start failed"); });

            stub.status();

            assertThat(stub.status()).isCompletedExceptionally();
        }

        @Test
        @DisplayName("snapshot reflects FAILED if startEvaluation throws")
        void snapshotFailedWhenStartThrows() {
            final var stub = new StubResult();
            stub.setStartBehavior(() -> { throw new IllegalStateException("start failed"); });

            stub.status();

            assertThat(stub.snapshot().getResult()).isEqualTo(Result.FAILED);
        }
    }

    // ── publication ordering ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("publication ordering")
    class PublicationOrdering {

        @Test
        @DisplayName("snapshot reflects terminal result when whenComplete callback fires (normal)")
        void snapshotTerminalAtNormalCompletion() {
            final var stub = new StubResult();
            final AtomicReference<Result> resultAtCallback = new AtomicReference<>();

            stub.status().whenComplete((r, t) -> resultAtCallback.set(stub.snapshot().getResult()));
            stub.completeWith(Result.VALID);

            assertThat(resultAtCallback.get()).isEqualTo(Result.VALID);
        }

        @Test
        @DisplayName("snapshot reflects FAILED when whenComplete callback fires (exceptional)")
        void snapshotFailedAtExceptionalCompletion() {
            final var stub = new StubResult();
            final AtomicReference<Result> resultAtCallback = new AtomicReference<>();

            stub.status().whenComplete((r, t) -> resultAtCallback.set(stub.snapshot().getResult()));
            stub.completeWithException(new RuntimeException("oops"));

            assertThat(resultAtCallback.get()).isEqualTo(Result.FAILED);
        }
    }
}
