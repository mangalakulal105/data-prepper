/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package com.amazon.dataprepper.plugins.source.loghttp;

import com.amazon.dataprepper.model.configuration.PluginSetting;

public class LogHTTPSourceConfig {
    static final String PORT = "port";
    static final String REQUEST_TIMEOUT = "request_timeout";
    static final String THREAD_COUNT = "thread_count";
    static final String MAX_CONNECTION_COUNT = "max_connection_count";
    static final String MAX_PENDING_REQUESTS = "max_pending_requests";
    static final int DEFAULT_PORT = 2021;
    static final int DEFAULT_REQUEST_TIMEOUT_MS = 10000;
    static final int DEFAULT_THREAD_COUNT = 200;
    static final int DEFAULT_MAX_CONNECTION_COUNT = 500;
    static final int DEFAULT_MAX_PENDING_REQUESTS = 1024;

    private final int port;
    private final int requestTimeoutInMillis;
    private final int threadCount;
    private final int maxConnectionCount;
    private final int maxPendingRequests;

    private LogHTTPSourceConfig(final int port,
                                final int requestTimeoutInMillis,
                                final int threadCount,
                                final int maxConnectionCount,
                                final int maxPendingRequests) {
        this.port = port;
        this.requestTimeoutInMillis = requestTimeoutInMillis;
        this.threadCount = threadCount;
        this.maxConnectionCount = maxConnectionCount;
        this.maxPendingRequests = maxPendingRequests;
    }

    public static LogHTTPSourceConfig buildConfig(final PluginSetting pluginSetting) {
        return new LogHTTPSourceConfig(
                pluginSetting.getIntegerOrDefault(PORT, DEFAULT_PORT),
                pluginSetting.getIntegerOrDefault(REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT_MS),
                pluginSetting.getIntegerOrDefault(THREAD_COUNT, DEFAULT_THREAD_COUNT),
                pluginSetting.getIntegerOrDefault(MAX_CONNECTION_COUNT, DEFAULT_MAX_CONNECTION_COUNT),
                pluginSetting.getIntegerOrDefault(MAX_PENDING_REQUESTS, DEFAULT_MAX_PENDING_REQUESTS)
        );
    }

    public int getPort() {
        return port;
    }

    public int getRequestTimeoutInMillis() {
        return requestTimeoutInMillis;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getMaxConnectionCount() {
        return maxConnectionCount;
    }

    public int getMaxPendingRequests() {
        return maxPendingRequests;
    }
}
