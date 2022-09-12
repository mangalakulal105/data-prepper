/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder.server;

import com.amazon.dataprepper.model.event.DefaultEventMetadata;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.JacksonEvent;
import com.amazon.dataprepper.model.record.Record;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.annotation.Post;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderConfiguration;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderProvider;
import org.opensearch.dataprepper.peerforwarder.PeerForwarderReceiveBuffer;
import org.opensearch.dataprepper.peerforwarder.model.WireEvent;
import org.opensearch.dataprepper.peerforwarder.model.WireEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final ResponseHandler responseHandler;
    private final PeerForwarderProvider peerForwarderProvider;
    private final PeerForwarderConfiguration peerForwarderConfiguration;
    private final ObjectMapper objectMapper;

    public PeerForwarderHttpService(final ResponseHandler responseHandler,
                                    final PeerForwarderProvider peerForwarderProvider,
                                    final PeerForwarderConfiguration peerForwarderConfiguration,
                                    final ObjectMapper objectMapper) {
        this.responseHandler = responseHandler;
        this.peerForwarderProvider = peerForwarderProvider;
        this.peerForwarderConfiguration = peerForwarderConfiguration;
        this.objectMapper = objectMapper;
    }

    @Post
    public HttpResponse doPost(final AggregatedHttpRequest aggregatedHttpRequest) {
        return processRequest(aggregatedHttpRequest);
    }

    private HttpResponse processRequest(final AggregatedHttpRequest aggregatedHttpRequest) {

        WireEvents wireEvents;
        final HttpData content = aggregatedHttpRequest.content();
        try {
            wireEvents = objectMapper.readValue(content.toStringUtf8(), WireEvents.class);
        } catch (JsonProcessingException e) {
            final String message = "Failed to write the request content due to bad request data format. Needs to be JSON object";
            LOG.error(message, e);
            return responseHandler.handleException(e, message);
        }

        writeEventsToBuffer(wireEvents, content);

        return HttpResponse.of(HttpStatus.OK);
    }

    private void writeEventsToBuffer(final WireEvents wireEvents, final HttpData content) {
        final PeerForwarderReceiveBuffer<Record<?>> recordPeerForwarderReceiveBuffer = getPeerForwarderBuffer(wireEvents);

        if (wireEvents.getEvents() != null) {
            final Collection<Record<?>> jacksonEvents = wireEvents.getEvents().stream()
                    .map(this::transformEvent)
                    .collect(Collectors.toList());

            try {
                recordPeerForwarderReceiveBuffer.writeAll(jacksonEvents, peerForwarderConfiguration.getRequestTimeout());
            } catch (Exception e) {
                LOG.error("Failed to write the request content [{}] due to:", content.toStringUtf8(), e);
            }
        }
    }

    private PeerForwarderReceiveBuffer<Record<?>> getPeerForwarderBuffer(final WireEvents wireEvents) {
        final String destinationPluginId = wireEvents.getDestinationPluginId();
        final String destinationPipelineName = wireEvents.getDestinationPipelineName();

        final Map<String, Map<String, PeerForwarderReceiveBuffer<Record<?>>>> pipelinePeerForwarderReceiveBufferMap =
                peerForwarderProvider.getPipelinePeerForwarderReceiveBufferMap();

        return pipelinePeerForwarderReceiveBufferMap
                .get(destinationPipelineName).get(destinationPluginId);
    }

    private Record<Event> transformEvent(final WireEvent wireEvent) {
        final JacksonEvent jacksonEvent = JacksonEvent.builder()
                .withEventMetadata(getEventMetadata(wireEvent))
                .withData(wireEvent.getEventData())
                .build();

        return new Record<>(jacksonEvent);
    }

    private DefaultEventMetadata getEventMetadata(final WireEvent wireEvent) {
        return DefaultEventMetadata.builder()
                .withEventType(wireEvent.getEventType())
                .withTimeReceived(wireEvent.getEventTimeReceived())
                .withAttributes(wireEvent.getEventAttributes())
                .build();
    }
}