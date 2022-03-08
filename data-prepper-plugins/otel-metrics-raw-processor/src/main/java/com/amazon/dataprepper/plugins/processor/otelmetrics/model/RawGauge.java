/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.processor.otelmetrics.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class RawGauge {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String startTime;

    private final String time;

    private final String kind;

    private final String value;

    private final String unit;

    private final String description;

    private final String name;

    private final String serviceName;

    private final Map<String, Object> attributes;


    RawGauge(RawGaugeBuilder builder) {
       this.attributes = builder.getAttributes();
       this.serviceName = builder.getServiceName();
       this.startTime = builder.getStartTime();
       this.time = builder.getTime();
       this.kind = builder.getKind();
       this.value = builder.getValue();
       this.unit = builder.getUnit();
       this.description = builder.getDescription();
       this.name = builder.getName();
    }

    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public String getTime() {
        return time;
    }

    public String getValue() {
        return value;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public String getName() {
        return name;
    }

    public String toJson() throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(this);
    }

}
