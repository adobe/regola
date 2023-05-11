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

package com.adobe.abp.regola.mockdatafetchers;

import com.adobe.abp.regola.datafetchers.Context;
import com.adobe.abp.regola.datafetchers.DataFetcher;
import com.adobe.abp.regola.datafetchers.DataFetcherConfiguration;
import com.adobe.abp.regola.datafetchers.FetchResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is to be used for test purposes only
 */
public class FixedDelayedWithSlaDataFetcher extends DataFetcher<Object, FixedDelayedWithSlaDataFetcher.RemoteContext> {

    private final int waitTime;

    public FixedDelayedWithSlaDataFetcher(int waitTime, long slaFetchTime) {
        super(new DataFetcherConfiguration().setSlaFetchTime(slaFetchTime));
        this.waitTime = waitTime;
    }

    @Override
    public CompletableFuture<FetchResponse<Object>> fetchResponse(RemoteContext context) {
        return CompletableFuture.supplyAsync(() -> {
            wait(waitTime);
            return FetchResponseUtils.makeTestResponse();
        });
    }

    public static void wait(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class RemoteContext implements Context {
    }
}
