/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.codec.eventjson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.log.JacksonLog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class EventJsonInputOutputCodecTest {
    private static final Integer BYTEBUFFER_SIZE = 1024;

    private ByteArrayOutputStream outputStream;

    @Mock
    private EventJsonOutputCodecConfig eventJsonOutputCodecConfig;

    private EventJsonOutputCodec outputCodec;
    private EventJsonInputCodec inputCodec;

    @BeforeEach
    public void setup() {
        outputStream = new ByteArrayOutputStream(BYTEBUFFER_SIZE);
    }

    public EventJsonOutputCodec createOutputCodec() {
        return new EventJsonOutputCodec(eventJsonOutputCodecConfig);
    }

    public EventJsonInputCodec createInputCodec() {
        return new EventJsonInputCodec();
    }

    @Test
    public void basicTest() throws Exception {
        final String key = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        Map<String, Object> data = Map.of(key, value);

        Instant startTime = Instant.now();
        Event event = createEvent(data, startTime);
        outputCodec = createOutputCodec();
        inputCodec = createInputCodec();
        outputCodec.start(outputStream, null, null);
        outputCodec.writeEvent(event, outputStream);
        outputCodec.complete(outputStream);
        inputCodec.parse(new ByteArrayInputStream(outputStream.toByteArray()), record -> {
            Event e = record.getData();
            assertThat(e.get(key, String.class), equalTo(value));
            assertThat(e.getMetadata().getTimeReceived(), equalTo(startTime));
            assertThat(e.getMetadata().getTags().size(), equalTo(0));
            assertThat(e.getMetadata().getExternalOriginationTime(), equalTo(null));
        });
    }

    @Test
    public void extendedTest() throws Exception {
        final String key = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        Map<String, Object> data = Map.of(key, value);

        Set<String> tags = Set.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        List<String> tagsList = tags.stream().collect(Collectors.toList());
        Instant startTime = Instant.now();
        Event event = createEvent(data, startTime);
        Instant origTime = startTime.minusSeconds(5);
        event.getMetadata().setExternalOriginationTime(origTime);
        event.getMetadata().addTags(tagsList);
        outputCodec = createOutputCodec();
        inputCodec = createInputCodec();
        outputCodec.start(outputStream, null, null);
        outputCodec.writeEvent(event, outputStream);
        outputCodec.complete(outputStream);
        inputCodec.parse(new ByteArrayInputStream(outputStream.toByteArray()), record -> {
            Event e = record.getData();
            assertThat(e.get(key, String.class), equalTo(value));
            assertThat(e.getMetadata().getTimeReceived(), equalTo(startTime));
            assertThat(e.getMetadata().getTags(), equalTo(tags));
            assertThat(e.getMetadata().getExternalOriginationTime(), equalTo(origTime));
        });
    }


    private Event createEvent(final Map<String, Object> json, final Instant timeReceived) {
        final JacksonLog.Builder logBuilder = JacksonLog.builder()
                .withData(json)
                .getThis();
        if (timeReceived != null) {
            logBuilder.withTimeReceived(timeReceived);
        }
        final JacksonEvent event = (JacksonEvent)logBuilder.build();

        return event;
    }
}

