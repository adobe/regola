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
import com.adobe.abp.regola.datafetchers.FetchResponse;
import java.util.concurrent.CompletableFuture;

/**
 * This data fetcher always returns an empty object.
 * This should be used as a placeholder data fetcher.
 */
public class EmptyObjectDataFetcher extends DataFetcher<Object, Context> {

    @Override
    public CompletableFuture<FetchResponse<Object>> fetchResponse(Context context) {
        return CompletableFuture.supplyAsync(FetchResponseUtils::makeTestResponse);
    }

    @Override
    public String calculateRequestKey(Context context) {
        return "same-key";
    }
}
