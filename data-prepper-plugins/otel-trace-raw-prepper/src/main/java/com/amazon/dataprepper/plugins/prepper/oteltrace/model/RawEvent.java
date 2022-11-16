/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.prepper.oteltrace.model;

import io.opentelemetry.proto.trace.v1.Span;

import java.util.Map;

/**
 * Java POJO of https://github.com/open-telemetry/opentelemetry-proto/blob/master/opentelemetry/proto/trace/v1/trace.proto#L169
 * which is compatible with OpenSearch
 */
public final class RawEvent {
    private final String time;
    private final String name;
    private final Map<String, Object> attributes;
    private final int droppedAttributesCount;

    public String getTime() {
        return time;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    private RawEvent(final String time, final String name, final Map<String, Object> attributes, int droppedAttributesCount) {
        this.time = time;
        this.name = name;
        this.attributes = attributes;
        this.droppedAttributesCount = droppedAttributesCount;
    }

    public static RawEvent buildRawEvent(final Span.Event event) {
       return new RawEvent(OTelProtoHelper.getTimeISO8601(event), event.getName(), OTelProtoHelper.getEventAttributes(event), event.getDroppedAttributesCount());
    }

}
