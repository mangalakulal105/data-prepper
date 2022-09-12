/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder.client;

import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.JacksonEvent;
import com.amazon.dataprepper.model.log.JacksonLog;
import com.amazon.dataprepper.model.record.Record;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.dataprepper.peerforwarder.PeerForwarderConfiguration.DEFAULT_PEER_FORWARDING_URI;

@ExtendWith(MockitoExtension.class)
class PeerForwarderClientTest {

    private static final String LOCAL_IP = "127.0.0.1";
    private static final String TEST_PLUGIN_ID = "test_plugin_id";
    private static final String TEST_PIPELINE_NAME = "test_pipeline_name";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Mock
    PeerForwarderConfiguration peerForwarderConfiguration;

    @Mock
    PeerClientPool peerClientPool;

    @Mock
    PeerForwarderClientFactory peerForwarderClientFactory;

    PeerForwarderClient createObjectUnderTest(final ObjectMapper objectMapper) {
        when(peerForwarderConfiguration.getClientThreadCount()).thenReturn(200);
        return new PeerForwarderClient(peerForwarderConfiguration, peerForwarderClientFactory, objectMapper);
    }

    @Test
    void test_serializeRecordsAndSendHttpRequest_with_actual_client_and_server_should_return() {
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
        server.stop(0);
    }

    @Test
    void test_serializeRecordsAndSendHttpRequest_with_bad_wireEvents_should_return_BAD_REQUEST() throws JsonProcessingException {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        lenient().when(objectMapper.writeValueAsString(instanceOf(WireEvents.class))).thenThrow(JsonProcessingException.class);

        when(peerForwarderClientFactory.setPeerClientPool()).thenReturn(peerClientPool);

        final PeerForwarderClient peerForwarderClient = createObjectUnderTest(objectMapper);

        final AggregatedHttpResponse aggregatedHttpResponse =
                peerForwarderClient.serializeRecordsAndSendHttpRequest(generateBatchRecords(1), LOCAL_IP, TEST_PLUGIN_ID, TEST_PIPELINE_NAME);

        assertThat(aggregatedHttpResponse, notNullValue());
        assertThat(aggregatedHttpResponse, instanceOf(AggregatedHttpResponse.class));
        assertThat(aggregatedHttpResponse.status(), equalTo(HttpStatus.BAD_REQUEST));
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
