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

package com.adobe.abp.regola.datafetchers.metrics;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogMetricsAgent implements MetricsAgent {

    private static final Logger LOG = Logger.getLogger(LogMetricsAgent.class.getName());

    private final MetricsAgentConfiguration configuration;

    public LogMetricsAgent() {
        this(new MetricsAgentConfiguration());
    }

    public LogMetricsAgent(MetricsAgentConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void onSuccess(String fetcherName, String requestKey, long requestTime) {
        if (configuration.isLogInfoMetrics()) {
            LOG.log(Level.INFO, String.format("Fetcher=%s - request=%s returned in %dms.",
                    fetcherName, requestKey, requestTime));
        }
    }

    @Override
    public void onFailure(String fetcherName, String requestKey, Throwable throwable, long requestTime) {
        if (configuration.isLogInfoMetrics()) {
            LOG.log(Level.WARNING, String.format("Fetcher=%s - request=%s failed with message=%s in %dms.",
                    fetcherName, requestKey, throwable.getMessage(), requestTime), throwable);
        }
    }

    @Override
    public void onSlaBreach(String fetcherName, String requestKey, long slaFetchTime, double requestTime) {
        if (configuration.isLogInfoMetrics()) {
            LOG.log(Level.WARNING, String.format("Fetcher=%s - request=%s failed to meet SLA since fetchTime=%.2f is greater than slaFetchTime=%d",
                    fetcherName, requestKey, requestTime, slaFetchTime));
        }
    }
}
