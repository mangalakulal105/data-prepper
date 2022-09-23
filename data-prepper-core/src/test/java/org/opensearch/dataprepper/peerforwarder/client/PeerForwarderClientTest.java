/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder.client;

import com.amazon.dataprepper.metrics.MetricNames;
import com.amazon.dataprepper.metrics.MetricsTestUtil;
import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.JacksonEvent;
import com.amazon.dataprepper.model.log.JacksonLog;
import com.amazon.dataprepper.model.record.Record;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.peerforwarder.PeerClientPool;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderClientFactory;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderConfiguration;
import org.opensearch.dataprepper.peerforwarder.model.WireEvents;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.dataprepper.peerforwarder.PeerForwarderConfiguration.DEFAULT_PEER_FORWARDING_URI;
import static org.opensearch.dataprepper.peerforwarder.client.PeerForwarderClient.LATENCY;
import static org.opensearch.dataprepper.peerforwarder.client.PeerForwarderClient.REQUESTS;

@ExtendWith(MockitoExtension.class)
class PeerForwarderClientTest {

    private static final String LOCAL_IP = "127.0.0.1";
    private static final String TEST_PLUGIN_ID = "test_plugin_id";
    private static final String TEST_PIPELINE_NAME = "test_pipeline_name";
    private static final String COMPONENT_SCOPE = "testComponentScope";
    private static final String COMPONENT_ID = "testComponentId";

    private ObjectMapper objectMapper;
    private PluginMetrics pluginMetrics;

    @Mock
    PeerForwarderConfiguration peerForwarderConfiguration;

    @Mock
    PeerClientPool peerClientPool;

    @Mock
    PeerForwarderClientFactory peerForwarderClientFactory;

    @BeforeEach
    void setUp() {
        MetricsTestUtil.initMetrics();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        pluginMetrics = PluginMetrics.fromNames(COMPONENT_ID, COMPONENT_SCOPE);

        when(peerForwarderClientFactory.setPeerClientPool()).thenReturn(peerClientPool);
    }

    private PeerForwarderClient createObjectUnderTest(final ObjectMapper objectMapper) {
        when(peerForwarderConfiguration.getClientThreadCount()).thenReturn(200);
        return new PeerForwarderClient(peerForwarderConfiguration, peerForwarderClientFactory, objectMapper, pluginMetrics);
    }

    @Test
    void test_serializeRecordsAndSendHttpRequest_with_actual_client_and_server_should_return() {
        MetricsTestUtil.initMetrics();
        when(peerForwarderClientFactory.setPeerClientPool()).thenReturn(peerClientPool);

        final HttpServer server = createServer(2022);
        server.createContext(DEFAULT_PEER_FORWARDING_URI, new TestHandler());
        server.start();

        final InetSocketAddress address = server.getAddress();
        final WebClient testClient = getTestClient(String.valueOf(address.getPort()));
        when(peerClientPool.getClient(anyString())).thenReturn(testClient);

        final PeerForwarderClient peerForwarderClient = createObjectUnderTest(objectMapper);

        final AggregatedHttpResponse aggregatedHttpResponse =
                peerForwarderClient.serializeRecordsAndSendHttpRequest(generateBatchRecords(1), address.toString(), TEST_PLUGIN_ID, TEST_PIPELINE_NAME);

        assertThat(aggregatedHttpResponse, notNullValue());
        assertThat(aggregatedHttpResponse, instanceOf(AggregatedHttpResponse.class));
        assertThat(aggregatedHttpResponse.status(), equalTo(HttpStatus.OK));

        final List<Measurement> forwardRequestTimerMeasurement = MetricsTestUtil.getMeasurementList(new StringJoiner(MetricNames.DELIMITER).add(COMPONENT_SCOPE).add(COMPONENT_ID)
                .add(LATENCY).toString());
        final List<Measurement> forwardedRequestCounterMeasurement = MetricsTestUtil.getMeasurementList(new StringJoiner(MetricNames.DELIMITER).add(COMPONENT_SCOPE).add(COMPONENT_ID)
                .add(REQUESTS).toString());
        assertThat(forwardedRequestCounterMeasurement.size(), equalTo(1));

        final Measurement forwardRequestTimerCount = MetricsTestUtil.getMeasurementFromList(forwardRequestTimerMeasurement, Statistic.COUNT);
        final Measurement forwardedRequestCounterCount = MetricsTestUtil.getMeasurementFromList(forwardedRequestCounterMeasurement, Statistic.COUNT);
        assertThat(forwardRequestTimerCount.getValue(), equalTo(1.0));
        assertThat(forwardedRequestCounterCount.getValue(), equalTo(1.0));

        server.stop(0);
    }

    @Test
    void test_serializeRecordsAndSendHttpRequest_with_bad_wireEvents_should_throw() throws JsonProcessingException {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(isA(WireEvents.class))).thenThrow(JsonProcessingException.class);

        final PeerForwarderClient objectUnderTest = createObjectUnderTest(objectMapper);

        final Collection<Record<Event>> records = generateBatchRecords(1);

        final RuntimeException actualException = assertThrows(RuntimeException.class,
                () -> objectUnderTest.serializeRecordsAndSendHttpRequest(records, "127.0.0.1", TEST_PLUGIN_ID, TEST_PIPELINE_NAME));

        assertThat(actualException.getCause(), instanceOf(JsonProcessingException.class));
    }

    private Collection<Record<Event>> generateBatchRecords(final int numRecords) {
        final Collection<Record<Event>> results = new ArrayList<>();
        for (int i = 0; i < numRecords; i++) {
            final Map<String, String> eventData = new HashMap<>();
            eventData.put("key1", "value" + i);
            eventData.put("key2", "value" + i);
            final JacksonEvent event = JacksonLog.builder().withData(eventData).build();
            results.add(new Record<>(event));
        }
        return results;
    }

    private WebClient getTestClient(final String port) {

        ClientBuilder clientBuilder = Clients.builder(String.format("%s://%s:%s/", "http", LOCAL_IP, port))
                .writeTimeout(Duration.ofSeconds(3));

        return clientBuilder.build(WebClient.class);
    }

    private HttpServer createServer(final int port) {
        final InetSocketAddress socketAddress = new InetSocketAddress(port);
        HttpServer httpServer = null;
        try {
            httpServer = HttpServer.create(socketAddress, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return httpServer;
    }

    private static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String response = "test server started";
            httpExchange.sendResponseHeaders(200, response.length());
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

}
