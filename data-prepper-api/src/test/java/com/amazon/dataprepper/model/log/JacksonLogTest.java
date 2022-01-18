/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.model.log;

import com.amazon.dataprepper.model.event.DefaultEventMetadata;
import com.amazon.dataprepper.model.event.EventMetadata;
import com.amazon.dataprepper.model.event.JacksonEvent;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JacksonLogTest {

    @Test
    public void testBuilder_usesLogEventType() {
        final Log log = JacksonLog.builder().build();

        assertThat(log, is(notNullValue()));
        assertThat(log.getMetadata().getEventType(), is(equalTo("LOG")));
    }

    @Test
    public void testBuilder_usesLogEventType_withUserProvidedEventType() {
        final Log log = JacksonLog.builder()
                .withEventType("test")
                .getThis()
                .build();

        assertThat(log, is(notNullValue()));
        assertThat(log.getMetadata().getEventType(), is(equalTo("LOG")));
    }

    @Test
    public void testBuilder_withNonLogMetadata_throwsIllegalArgumentException() {
        final EventMetadata eventMetadata = DefaultEventMetadata.builder()
                .withEventType("foobar")
                .build();

        final JacksonEvent.Builder<JacksonLog.Builder> logBuilder = JacksonLog.builder()
                .withEventMetadata(eventMetadata);

        assertThat(logBuilder, is(notNullValue()));
        assertThrows(IllegalArgumentException.class, logBuilder::build);
    }

    @Test
    public void testBuilder_usesLogEventType_withDefaultTimestamp() {
        final Log log = JacksonLog.builder()
                .withEventType("test")
                .getThis()
                .build();

        assertThat(log, is(notNullValue()));
        assertTrue(log.containsKey("event_timestamp"));
        assertThat(log.getMetadata().getEventType(), is(equalTo("LOG")));
    }
}
