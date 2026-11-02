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

/**
 * Test data fetcher that throws on the calling thread instead of returning a failed future.
 */
public class SynchronouslyFailingDataFetcher extends DataFetcher<String, Context> {

    @Override
    public String calculateRequestKey(Context context) {
        throw new IllegalStateException("Synchronous fetch failure");
    }

    @Override
    public CompletableFuture<FetchResponse<String>> fetchResponse(Context context) {
        throw new IllegalStateException("Synchronous fetch failure");
    }
}
