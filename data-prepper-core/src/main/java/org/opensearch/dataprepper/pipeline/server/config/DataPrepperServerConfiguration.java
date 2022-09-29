/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.pipeline.server.config;

import org.opensearch.dataprepper.DataPrepper;
import org.opensearch.dataprepper.model.configuration.PluginModel;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.plugin.PluginFactory;
import org.opensearch.dataprepper.pipeline.server.DataPrepperCoreAuthenticationProvider;
import org.opensearch.dataprepper.pipeline.server.HttpServerProvider;
import org.opensearch.dataprepper.pipeline.server.ListPipelinesHandler;
import org.opensearch.dataprepper.pipeline.server.PrometheusMetricsHandler;
import org.opensearch.dataprepper.pipeline.server.ShutdownHandler;
import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Nullable;
import java.util.Collections;

@Configuration
public class DataPrepperServerConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(DataPrepperServerConfiguration.class);

    private void createContext(
            final HttpServer httpServer,
            final HttpHandler httpHandler,
            @Nullable final Authenticator authenticator,
            final String ... paths
    ) {
        for (final String path : paths) {
            final HttpContext context = httpServer.createContext(path, httpHandler);

            if (authenticator != null) {
                context.setAuthenticator(authenticator);
            }
        }
    }

    @Bean
    public HttpServer httpServer(
            final HttpServerProvider httpServerProvider,
            final ListPipelinesHandler listPipelinesHandler,
            final ShutdownHandler shutdownHandler,
            @Autowired(required = false) @Nullable final PrometheusMeterRegistry prometheusMeterRegistry,
            @Autowired(required = false) @Nullable final Authenticator authenticator
            ) {

        final HttpServer server = httpServerProvider.get();

        createContext(server, listPipelinesHandler, authenticator, "/list");
        createContext(server, shutdownHandler, authenticator, "/shutdown");

        if (prometheusMeterRegistry != null) {
            final PrometheusMetricsHandler prometheusMetricsHandler = new PrometheusMetricsHandler(prometheusMeterRegistry);
            createContext(server, prometheusMetricsHandler, authenticator, "/metrics/prometheus", "/metrics/sys");
        }

        return server;
    }

    private void printInsecurePluginModelWarning() {
        LOG.warn("Creating data prepper server without authentication. This is not secure.");
        LOG.warn("In order to set up Http Basic authentication for the data prepper server, " +
                "go here: https://github.com/opensearch-project/data-prepper/blob/main/docs/core_apis.md#authentication");
    }

    @Bean
    public PluginSetting pluginSetting(@Autowired(required = false) final PluginModel authentication) {
        if (authentication != null) {
            final String pluginName = authentication.getPluginName();
            if (pluginName.equals(DataPrepperCoreAuthenticationProvider.UNAUTHENTICATED_PLUGIN_NAME)) {
                printInsecurePluginModelWarning();
            }
            return new PluginSetting(pluginName, authentication.getPluginSettings());
        }
        else {
            printInsecurePluginModelWarning();
            return new PluginSetting(
                    DataPrepperCoreAuthenticationProvider.UNAUTHENTICATED_PLUGIN_NAME,
                    Collections.emptyMap());
        }
    }

    @Bean
    public DataPrepperCoreAuthenticationProvider authenticationProvider(
            final PluginFactory pluginFactory,
            final PluginSetting pluginSetting
    ) {
        return pluginFactory.loadPlugin(
                DataPrepperCoreAuthenticationProvider.class,
                pluginSetting
        );
    }

    @Bean
    public Authenticator authenticator(final DataPrepperCoreAuthenticationProvider authenticationProvider) {
        return authenticationProvider.getAuthenticator();
    }

    @Bean
    public ListPipelinesHandler listPipelinesHandler(final DataPrepper dataPrepper) {
        return new ListPipelinesHandler(dataPrepper);
    }

    @Bean
    public ShutdownHandler shutdownHandler(final DataPrepper dataPrepper) {
        return new ShutdownHandler(dataPrepper);
    }
}
