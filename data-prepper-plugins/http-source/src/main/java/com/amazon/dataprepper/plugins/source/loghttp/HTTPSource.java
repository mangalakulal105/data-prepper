/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.source.loghttp;

import com.amazon.dataprepper.armeria.authentication.ArmeriaHttpAuthenticationProvider;
import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.annotations.DataPrepperPluginConstructor;
import com.amazon.dataprepper.model.buffer.Buffer;
import com.amazon.dataprepper.model.configuration.PluginModel;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.plugin.PluginFactory;
import com.amazon.dataprepper.model.log.Log;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.model.source.Source;
import com.amazon.dataprepper.plugins.certificate.CertificateProvider;
import com.amazon.dataprepper.plugins.certificate.model.Certificate;
import com.amazon.dataprepper.plugins.source.loghttp.certificate.CertificateProviderFactory;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.healthcheck.HealthCheckService;
import com.linecorp.armeria.server.throttling.ThrottlingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Function;

@DataPrepperPlugin(name = "http", pluginType = Source.class, pluginConfigurationType = HTTPSourceConfig.class)
public class HTTPSource implements Source<Record<Log>> {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPSource.class);
    public static final String REGEX_HEALTH = "regex:^/(?!health$).*$";

    private final HTTPSourceConfig sourceConfig;
    private final CertificateProviderFactory certificateProviderFactory;
    private final ArmeriaHttpAuthenticationProvider authenticationProvider;
    private Server server;
    private final PluginMetrics pluginMetrics;
    private static final String HTTP_HEALTH_CHECK_PATH = "/health";

    @DataPrepperPluginConstructor
    public HTTPSource(final HTTPSourceConfig sourceConfig, final PluginMetrics pluginMetrics, final PluginFactory pluginFactory) {
        this.sourceConfig = sourceConfig;
        this.pluginMetrics = pluginMetrics;
        certificateProviderFactory = new CertificateProviderFactory(sourceConfig);
        final PluginModel authenticationConfiguration = sourceConfig.getAuthentication();
        final PluginSetting authenticationPluginSetting;

        if (authenticationConfiguration == null || authenticationConfiguration.getPluginName().equals(ArmeriaHttpAuthenticationProvider.UNAUTHENTICATED_PLUGIN_NAME)) {
            LOG.warn("Creating http source without authentication. This is not secure.");
            LOG.warn("In order to set up Http Basic authentication for the http source, go here: https://github.com/opensearch-project/data-prepper/tree/main/data-prepper-plugins/http-source#authentication-configurations");
        }

        if(authenticationConfiguration != null) {
            authenticationPluginSetting =
                    new PluginSetting(authenticationConfiguration.getPluginName(), authenticationConfiguration.getPluginSettings());
        } else {
            authenticationPluginSetting =
                    new PluginSetting(ArmeriaHttpAuthenticationProvider.UNAUTHENTICATED_PLUGIN_NAME, Collections.emptyMap());
        }
        authenticationProvider = pluginFactory.loadPlugin(ArmeriaHttpAuthenticationProvider.class, authenticationPluginSetting);
    }

    @Override
    public void start(final Buffer<Record<Log>> buffer) {
        if (buffer == null) {
            throw new IllegalStateException("Buffer provided is null");
        }
        if (server == null) {
            final ServerBuilder sb = Server.builder();

            sb.disableServerHeader();

            if (sourceConfig.isSsl()) {
                LOG.info("Creating http source with SSL/TLS enabled.");
                final CertificateProvider certificateProvider = certificateProviderFactory.getCertificateProvider();
                final Certificate certificate = certificateProvider.getCertificate();
                // TODO: enable encrypted key with password
                sb.https(sourceConfig.getPort()).tls(
                        new ByteArrayInputStream(certificate.getCertificate().getBytes(StandardCharsets.UTF_8)),
                        new ByteArrayInputStream(certificate.getPrivateKey().getBytes(StandardCharsets.UTF_8)
                        )
                );
            } else {
                LOG.warn("Creating http source without SSL/TLS. This is not secure.");
                LOG.warn("In order to set up TLS for the http source, go here: https://github.com/opensearch-project/data-prepper/tree/main/data-prepper-plugins/http-source#ssl");
                sb.http(sourceConfig.getPort());
            }

            if(sourceConfig.getAuthentication() != null) {
                final Optional<Function<? super HttpService, ? extends HttpService>> optionalAuthDecorator = authenticationProvider.getAuthenticationDecorator();

                if (sourceConfig.isUnauthenticatedHealthCheck()) {
                    optionalAuthDecorator.ifPresent(authDecorator -> sb.decorator(REGEX_HEALTH, authDecorator));
                } else {
                    optionalAuthDecorator.ifPresent(sb::decorator);
                }
            }

            sb.maxNumConnections(sourceConfig.getMaxConnectionCount());
            final int requestTimeoutInMillis = sourceConfig.getRequestTimeoutInMillis();
            // Allow 2*requestTimeoutInMillis to accommodate non-blocking operations other than buffer writing.
            sb.requestTimeout(Duration.ofMillis(2*requestTimeoutInMillis));
            final int threads = sourceConfig.getThreadCount();
            final ScheduledThreadPoolExecutor blockingTaskExecutor = new ScheduledThreadPoolExecutor(threads);
            sb.blockingTaskExecutor(blockingTaskExecutor, true);
            final int maxPendingRequests = sourceConfig.getMaxPendingRequests();
            final LogThrottlingStrategy logThrottlingStrategy = new LogThrottlingStrategy(
                    maxPendingRequests, blockingTaskExecutor.getQueue());
            final LogThrottlingRejectHandler logThrottlingRejectHandler = new LogThrottlingRejectHandler(maxPendingRequests, pluginMetrics);
            // TODO: allow customization on URI path for log ingestion
            sb.decorator(HTTPSourceConfig.DEFAULT_LOG_INGEST_URI, ThrottlingService.newDecorator(logThrottlingStrategy, logThrottlingRejectHandler));
            final LogHTTPService logHTTPService = new LogHTTPService(requestTimeoutInMillis, buffer, pluginMetrics);
            sb.annotatedService(HTTPSourceConfig.DEFAULT_LOG_INGEST_URI, logHTTPService);

            if (sourceConfig.hasHealthCheckService()) {
                LOG.info("HTTP source health check is enabled");
                sb.service(HTTP_HEALTH_CHECK_PATH, HealthCheckService.builder().longPolling(0).build());
            }

            server = sb.build();
        }

        try {
            server.start().get();
        } catch (ExecutionException ex) {
            if (ex.getCause() != null && ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            } else {
                throw new RuntimeException(ex);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
        LOG.info("Started http source on port " + sourceConfig.getPort() + "...");
    }

    @Override
    public void stop() {
        if (server != null) {
            try {
                server.stop().get();
            } catch (ExecutionException ex) {
                if (ex.getCause() != null && ex.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) ex.getCause();
                } else {
                    throw new RuntimeException(ex);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }
        LOG.info("Stopped http source.");
    }
}
