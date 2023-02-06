/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.peerforwarder.model;

import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.peerforwarder.PeerForwarder;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * A class for {@link org.opensearch.dataprepper.model.event.EventType} and JSON representation of event data used by {@link PeerForwarder}
 *
 * @since 2.0
 */
public class WireEvent implements Serializable {
    private String eventType;
    private Instant eventTimeReceived;
    private Map<String, Object> eventAttributes;
    private Event eventData;

    public WireEvent() {
    }

    // TODO: Add a toJsonString method to EventMetadata and use that instead of metadata fields

    public WireEvent(final String eventType,
                     final Instant eventTimeReceived,
                     final Map<String, Object> eventAttributes,
                     final Event eventData) {
        this.eventType = eventType;
        this.eventTimeReceived = eventTimeReceived;
        this.eventAttributes = eventAttributes;
        this.eventData = eventData;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getEventTimeReceived() {
        return eventTimeReceived;
    }

    public Map<String, Object> getEventAttributes() {
        return eventAttributes;
    }

    public Event getEventData() {
        return eventData;
    }
}
