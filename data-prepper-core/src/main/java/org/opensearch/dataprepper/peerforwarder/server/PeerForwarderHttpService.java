/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder.server;

import io.micrometer.core.instrument.Counter;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.annotation.Post;
import io.micrometer.core.instrument.Timer;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderConfiguration;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderProvider;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderReceiveBuffer;
import org.opensearch.dataprepper.peerforwarder.model.WireEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An annotated HTTP service class to handle POST requests used by {@link PeerForwarderHttpServerProvider}
 *
 * @since 2.0
 */
public class PeerForwarderHttpService {
    private static final Logger LOG = LoggerFactory.getLogger(PeerForwarderHttpService.class);
    private static final String TRACE_EVENT_TYPE = "TRACE";
    static final String SERVER_REQUEST_PROCESSING_LATENCY = "serverRequestProcessingLatency";
    static final String RECORDS_RECEIVED_FROM_PEERS = "recordsReceivedFromPeers";
    private static final double BUFFER_TIMEOUT_FRACTION = 0.8;

    private final ResponseHandler responseHandler;
    private final PeerForwarderProvider peerForwarderProvider;
    private final PeerForwarderConfiguration peerForwarderConfiguration;
    private final ObjectMapper objectMapper;
    private final Timer serverRequestProcessingLatencyTimer;
    private final Counter recordsReceivedFromPeersCounter;

    public PeerForwarderHttpService(final ResponseHandler responseHandler,
                                    final PeerForwarderProvider peerForwarderProvider,
                                    final PeerForwarderConfiguration peerForwarderConfiguration,
                                    final ObjectMapper objectMapper,
                                    final PluginMetrics pluginMetrics) {
        this.responseHandler = responseHandler;
        this.peerForwarderProvider = peerForwarderProvider;
        this.peerForwarderConfiguration = peerForwarderConfiguration;
        this.objectMapper = objectMapper;
        serverRequestProcessingLatencyTimer = pluginMetrics.timer(SERVER_REQUEST_PROCESSING_LATENCY);
        recordsReceivedFromPeersCounter = pluginMetrics.counter(RECORDS_RECEIVED_FROM_PEERS);
    }

    @Post
    public HttpResponse doPost(final AggregatedHttpRequest aggregatedHttpRequest) {
        return serverRequestProcessingLatencyTimer.record(() -> processRequest(aggregatedHttpRequest));
    }

    private HttpResponse processRequest(final AggregatedHttpRequest aggregatedHttpRequest) {

        WireEvents wireEvents;
        final HttpData content = aggregatedHttpRequest.content();
        try (final InputStream inputStream = new ByteArrayInputStream(content.array());
             final ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
            wireEvents = (WireEvents) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            final String message = "Failed to write the request content due to bad request data format. Needs to be JSON object";
            LOG.error(message, e);
            return responseHandler.handleException(e, message);
        }

        try {
            writeEventsToBuffer(wireEvents);
        } catch (Exception e) {
            final String message = String.format("Failed to write the request of size %d due to:", content.length());
            LOG.error(message, e);
            return responseHandler.handleException(e, message);
        }

        return HttpResponse.of(HttpStatus.OK);
    }

    private void writeEventsToBuffer(final WireEvents wireEvents) throws Exception {
        final PeerForwarderReceiveBuffer<Record<Event>> recordPeerForwarderReceiveBuffer = getPeerForwarderBuffer(wireEvents);

        if (wireEvents.getEvents() != null) {
            final Collection<Record<Event>> jacksonEvents = wireEvents.getEvents().stream()
                    .map(this::transformEvent)
                    .collect(Collectors.toList());

            recordPeerForwarderReceiveBuffer.writeAll(jacksonEvents, getBufferTimeoutMillis());
            recordsReceivedFromPeersCounter.increment(jacksonEvents.size());
        }
    }

    private int getBufferTimeoutMillis() {
        return (int) (peerForwarderConfiguration.getRequestTimeout() * BUFFER_TIMEOUT_FRACTION);
    }

    private PeerForwarderReceiveBuffer<Record<Event>> getPeerForwarderBuffer(final WireEvents wireEvents) {
        final String destinationPluginId = wireEvents.getDestinationPluginId();
        final String destinationPipelineName = wireEvents.getDestinationPipelineName();

        final Map<String, Map<String, PeerForwarderReceiveBuffer<Record<Event>>>> pipelinePeerForwarderReceiveBufferMap =
                peerForwarderProvider.getPipelinePeerForwarderReceiveBufferMap();

        return pipelinePeerForwarderReceiveBufferMap
                .get(destinationPipelineName).get(destinationPluginId);
    }

    private Record<Event> transformEvent(final Event event) {
        return new Record<>(event);
    }
}