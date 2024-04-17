/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.codec.eventjson;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.codec.OutputCodec;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.EventMetadata;
import org.opensearch.dataprepper.model.sink.OutputCodecContext;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@DataPrepperPlugin(name = "event_json", pluginType = OutputCodec.class, pluginConfigurationType = EventJsonOutputCodecConfig.class)
public class EventJsonOutputCodec implements OutputCodec {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String EVENT_JSON = "event_json";
    private static final JsonFactory factory = new JsonFactory();
    private final EventJsonOutputCodecConfig config;
    private JsonGenerator generator;
    private OutputCodecContext codecContext;

    @DataPrepperPluginConstructor
    public EventJsonOutputCodec(final EventJsonOutputCodecConfig config) {
        this.config = config;
    }

    @Override
    public String getExtension() {
        return EVENT_JSON;
    }

    @Override
    public void start(OutputStream outputStream, Event event, OutputCodecContext context) throws IOException {
        Objects.requireNonNull(outputStream);
        generator = factory.createGenerator(outputStream, JsonEncoding.UTF8);
        generator.writeStartObject();
    }

    @Override
    public void complete(final OutputStream outputStream) throws IOException {
        generator.writeEndObject();
        generator.close();
        outputStream.flush();
        outputStream.close();
    }

    @Override
    public synchronized void writeEvent(final Event event, final OutputStream outputStream) throws IOException {
        Objects.requireNonNull(event);
        try {
            getDataMapToSerialize(event);
        } catch (Exception e){
        }
        generator.flush();
    }

    private Map<String, Object> getDataMapToSerialize(Event event) throws Exception {
        Map<String, Object> dataMap = event.toMap();
        generator.writeFieldName(EventJsonDefines.DATA);
        objectMapper.writeValue(generator, dataMap);
        Map<String, Object> metadataMap = new HashMap<>();
        EventMetadata metadata = event.getMetadata();
        metadataMap.put(EventJsonDefines.ATTRIBUTES, metadata.getAttributes());
        metadataMap.put(EventJsonDefines.TAGS, metadata.getTags());
        metadataMap.put(EventJsonDefines.TIME_RECEIVED, metadata.getTimeReceived().toString());
        if (metadata.getExternalOriginationTime() != null) {
            metadataMap.put(EventJsonDefines.EXTERNAL_ORIGINATION_TIME, metadata.getExternalOriginationTime().toString());
        }
        generator.writeFieldName(EventJsonDefines.METADATA);
        objectMapper.writeValue(generator, metadataMap);
        return dataMap;
    }

}
