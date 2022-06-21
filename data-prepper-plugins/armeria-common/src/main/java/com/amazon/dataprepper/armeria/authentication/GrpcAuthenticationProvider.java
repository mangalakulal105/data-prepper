/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.armeria.authentication;

import com.linecorp.armeria.server.HttpService;
import io.grpc.ServerInterceptor;

import java.util.Optional;
import java.util.function.Function;

/**
 * An interface for providing authentication in GRPC servers.
 * <p>
 * Plugin authors can use this interface for Armeria authentication in
 * GRPC servers.
 *
 * @since 1.2
 */
public interface GrpcAuthenticationProvider {
    /**
     * The plugin name for the plugin which allows unauthenticated
     * requests. This plugin will disable authentication.
     */
    String UNAUTHENTICATED_PLUGIN_NAME = "unauthenticated";

    /**
     * Returns a {@link ServerInterceptor} that does authentication
     * @since 1.2
     */
    ServerInterceptor getAuthenticationInterceptor();

    /**
     * Returns an optional service for HTTP authentication
     * @since 1.5
     */
    default Optional<Function<? super HttpService, ? extends HttpService>> getHttpAuthenticationService() {
        return Optional.empty();
    }
}