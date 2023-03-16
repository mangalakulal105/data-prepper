/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.event;

import org.opensearch.dataprepper.model.event.DefaultEventMetadata;
import org.opensearch.dataprepper.model.event.EventMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Collections;
import java.util.Map;
import java.time.Instant;

class DefaultBaseEventBuilderTests {
    private DefaultBaseEventBuilder defaultBaseEventBuilder;
    class TestDefaultBaseEventBuilder extends DefaultBaseEventBuilder {
    }

    @BeforeEach
    void setup() {
        defaultBaseEventBuilder = new TestDefaultBaseEventBuilder();
    }

    @Test
    void testDefaultBaseEventBuilder() {
        assertThat(defaultBaseEventBuilder.getTimeReceived(), not(equalTo(null)));
    }

    @Test
    void testDefaultBaseEventBuilderWithTypeDataAndAttributes() {
        String testKey = RandomStringUtils.randomAlphabetic(5);
        String testValue = RandomStringUtils.randomAlphabetic(10);
        Map<String, Object> data = Map.of(testKey, testValue);
        defaultBaseEventBuilder.withData(data);
        String testEventType = RandomStringUtils.randomAlphabetic(10);
        defaultBaseEventBuilder.withEventType(testEventType);
        Map<String, Object> metadataAttributes = Collections.emptyMap();
        defaultBaseEventBuilder.withEventMetadataAttributes(metadataAttributes);
        
        assertThat(defaultBaseEventBuilder.getTimeReceived(), not(equalTo(null)));
        assertThat(defaultBaseEventBuilder.getData(), equalTo(data));
        assertThat(defaultBaseEventBuilder.getEventType(), equalTo(testEventType));
        assertThat(defaultBaseEventBuilder.getEventMetadataAttributes(), equalTo(metadataAttributes));
        assertThat(defaultBaseEventBuilder.getEventMetadata().getEventType(), equalTo(testEventType));
        Instant timeReceived = defaultBaseEventBuilder.getTimeReceived();
        assertThat(defaultBaseEventBuilder.getEventMetadata().getTimeReceived(), equalTo(timeReceived));
        assertThat(defaultBaseEventBuilder.getData(), equalTo(data));
    }

    @Test
    void testDefaultBaseEventBuilderWithEventMetadata() {
        String testKey = RandomStringUtils.randomAlphabetic(5);
        String testValue = RandomStringUtils.randomAlphabetic(10);
        Map<String, Object> data = Map.of(testKey, testValue);
        String testEventType = RandomStringUtils.randomAlphabetic(10);
        Instant timeReceived = Instant.now();
        Map<String, Object> attributes = Collections.emptyMap();
        EventMetadata eventMetadata = new DefaultEventMetadata.Builder()
                       .withEventType(testEventType)
                       .withTimeReceived(timeReceived)
                       .withAttributes(attributes)
                       .build();

        defaultBaseEventBuilder.withEventMetadata(eventMetadata);
        defaultBaseEventBuilder.withData(data);
        
        assertThat(defaultBaseEventBuilder.getTimeReceived(),equalTo(timeReceived));
        assertThat(defaultBaseEventBuilder.getData(), equalTo(data));
        assertThat(defaultBaseEventBuilder.getEventType(), equalTo(testEventType));
        assertThat(defaultBaseEventBuilder.getEventMetadataAttributes(), equalTo(attributes));
    }
}
