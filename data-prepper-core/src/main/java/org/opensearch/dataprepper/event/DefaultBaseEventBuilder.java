/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.event;

import org.opensearch.dataprepper.model.event.BaseEventBuilder;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.EventMetadata;
import org.opensearch.dataprepper.model.event.DefaultEventMetadata;

import java.util.Map;
import java.time.Instant;
import com.google.common.collect.ImmutableMap;

public abstract class DefaultBaseEventBuilder<T extends Event> implements BaseEventBuilder<T>{
    private EventMetadata eventMetadata;
    private Object data;
    private String eventType;
    private Instant timeReceived;
    private Map<String, Object> attributes;

    public DefaultBaseEventBuilder() {
        withTimeReceived(Instant.now());
    }

    public Object getData() {
        return this.data;
    }

    public String getEventType() {
        return this.eventType;
    }

    public EventMetadata getEventMetadata() {
        if (this.eventMetadata == null) {
            this.eventMetadata = new DefaultEventMetadata.Builder()
                           .withEventType(eventType)
                           .withTimeReceived(timeReceived)
                           .withAttributes(attributes)
                           .build();
        }
        return this.eventMetadata;
    }

    public Instant getTimeReceived() {
        return this.timeReceived;
    }

    public Map<String, Object> getEventMetadataAttributes() {
        return this.attributes;
    }

    public BaseEventBuilder<T> withEventType(final String eventType) {
        this.eventType = eventType;
        return this;
    }

    public BaseEventBuilder<T> withEventMetadataAttributes(final Map<String, Object> eventMetadataAttributes) {
        this.attributes = eventMetadataAttributes;
        return this;
    }

    public BaseEventBuilder<T> withTimeReceived(final Instant timeReceived) {
        this.timeReceived = timeReceived;
        return this;
    }

    public BaseEventBuilder<T> withEventMetadata(final EventMetadata eventMetadata) {
        this.eventType = eventMetadata.getEventType();
        this.timeReceived = eventMetadata.getTimeReceived();
        this.attributes = ImmutableMap.copyOf(eventMetadata.getAttributes());
        return this;
    }

    public BaseEventBuilder<T> withData(final Object data) {
        this.data = data;
        return this;
    }
}

