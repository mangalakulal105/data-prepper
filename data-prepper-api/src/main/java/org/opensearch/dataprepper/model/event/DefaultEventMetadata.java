/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.model.event;

import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A Basic implementation of {@link EventMetadata} interfaces utilizing an immutable map for attributes.
 *
 * @since 1.2
 */
public class DefaultEventMetadata implements EventMetadata {

    private final String eventType;

    private final Instant timeReceived;

    private final ImmutableMap<String, Object> attributes;

    private DefaultEventMetadata(final Builder builder) {

        checkNotNull(builder.eventType, "eventType cannot be null");
        checkArgument(!builder.eventType.isEmpty(), "eventType cannot be empty");

        this.eventType = builder.eventType;

        this.timeReceived = builder.timeReceived == null ? Instant.now() : builder.timeReceived;

        this.attributes = builder.attributes == null ? new ImmutableMap.Builder<String, Object>().build() : ImmutableMap.copyOf(builder.attributes);
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public Instant getTimeReceived() {
        return timeReceived;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Constructs an empty builder.
     * @return a builder
     * @since 1.2
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A Builder for creating {@link DefaultEventMetadata} instances.
     *
     * @since 1.2
     */
    public static class Builder {
        private String eventType;
        private Instant timeReceived;
        private Map<String, Object> attributes;

        /**
         * Sets the event type. A non-null or empty event type is required, otherwise it will cause {@link #build()} to fail.
         * @param eventType the event type
         * @since 1.2
         */
        public Builder withEventType(final String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * Sets the time received. If not provided, {@link Instant#now()} will be used to set the time received
         * @param timeReceived the time an event was received
         * @since 1.2
         */
        public Builder withTimeReceived(final Instant timeReceived) {
            this.timeReceived = timeReceived;
            return this;
        }

        /**
         * Sets the attributes. An empty immutable map is the default value.
         * @param attributes a map of key-value pair attributes
         * @since 1.2
         */
        public Builder withAttributes(final Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * Returns a newly created {@link DefaultEventMetadata}.
         * @return an {@link DefaultEventMetadata}
         * @since 1.2
         * @throws IllegalArgumentException if {@link #eventType} is an empty String
         * @throws NullPointerException if {@link #eventType} is null
         */
        public DefaultEventMetadata build() {
            return new DefaultEventMetadata(this);
        }
    }
}
