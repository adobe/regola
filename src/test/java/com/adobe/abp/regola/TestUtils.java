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

package com.adobe.abp.regola;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.IOUtils;

public class TestUtils {

    /**
     * A dedicated, uncapped executor for test-only mock rules/data-fetchers that simulate delays
     * with real {@code Thread.sleep}. Using {@link java.util.concurrent.ForkJoinPool#commonPool()}
     * (the default for {@code CompletableFuture.supplyAsync}) ties timing tests to the number of
     * CPU cores available (parallelism = cores - 1, minimum 1) and to contention from every other
     * test in the suite submitting work to that same shared pool. On cheap/low-core or busy CI
     * machines this can starve the common pool and make time-bound assertions (e.g. succeedsWithin/
     * failsWithin) flaky even though the simulated delay itself is small. Using a dedicated,
     * cached thread pool decouples these timing tests from that shared, size-limited resource.
     */
    public static final Executor DELAY_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            final var thread = new Thread(r, "test-delay-executor-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    public static <T> String readRules(Class<T> clazz, String filename) throws IOException {
        try (InputStream s = clazz.getResourceAsStream(filename)) {
            return IOUtils.toString(Objects.requireNonNull(s), StandardCharsets.UTF_8);
        }
    }
}
