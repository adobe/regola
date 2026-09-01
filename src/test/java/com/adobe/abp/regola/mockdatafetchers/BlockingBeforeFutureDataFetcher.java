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

package com.adobe.abp.regola.mockdatafetchers;

import com.adobe.abp.regola.datafetchers.Context;
import com.adobe.abp.regola.datafetchers.DataFetcher;
import com.adobe.abp.regola.datafetchers.FetchResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Test data fetcher that blocks synchronously before returning its future.
 */
public class BlockingBeforeFutureDataFetcher extends DataFetcher<String, Context> {

    private final String requestKey;
    private final String value;
    private final CountDownLatch started;
    private final CountDownLatch release;

    public BlockingBeforeFutureDataFetcher(
            String requestKey,
            String value,
            CountDownLatch started,
            CountDownLatch release) {
        this.requestKey = requestKey;
        this.value = value;
        this.started = started;
        this.release = release;
    }

    @Override
    public String calculateRequestKey(Context context) {
        return requestKey;
    }

    @Override
    public CompletableFuture<FetchResponse<String>> fetchResponse(Context context) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(exception);
        }

        final var response = new FetchResponse<String>();
        response.setData(value);
        return CompletableFuture.completedFuture(response);
    }
}
