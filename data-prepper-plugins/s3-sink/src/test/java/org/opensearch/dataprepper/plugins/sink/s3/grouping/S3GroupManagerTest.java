/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.s3.grouping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.plugins.sink.s3.S3SinkConfig;
import org.opensearch.dataprepper.plugins.sink.s3.accumulator.Buffer;
import org.opensearch.dataprepper.plugins.sink.s3.accumulator.BufferFactory;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Collection;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3GroupManagerTest {

    @Mock
    private S3SinkConfig s3SinkConfig;

    @Mock
    private S3GroupIdentifierFactory s3GroupIdentifierFactory;

    @Mock
    private BufferFactory bufferFactory;

    @Mock
    private S3Client s3Client;

    private S3GroupManager createObjectUnderTest() {
        return new S3GroupManager(s3SinkConfig, s3GroupIdentifierFactory, bufferFactory, s3Client);
    }

    @Test
    void hasNoGroups_returns_true_when_there_are_no_groups() {
        final S3GroupManager objectUnderTest = createObjectUnderTest();

        assertThat(objectUnderTest.hasNoGroups(), equalTo(true));
    }

    @Test
    void getOrCreateGroupForEvent_creates_expected_group_when_it_does_not_exist() {
        final Event event = mock(Event.class);
        final S3GroupIdentifier s3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(event)).thenReturn(s3GroupIdentifier);

        final Buffer buffer = mock(Buffer.class);
        when(bufferFactory.getBuffer(eq(s3Client), any(Supplier.class), any(Supplier.class)))
                .thenReturn(buffer);

        final S3GroupManager objectUnderTest = createObjectUnderTest();

        final S3Group result = objectUnderTest.getOrCreateGroupForEvent(event);

        assertThat(result, notNullValue());
        assertThat(result.getS3GroupIdentifier(), equalTo(s3GroupIdentifier));
        assertThat(result.getBuffer(), equalTo(buffer));

        final Collection<S3Group> groups = objectUnderTest.getS3GroupEntries();
        assertThat(groups, notNullValue());
        assertThat(groups.size(), equalTo(1));

        assertThat(groups.contains(result), equalTo(true));
        assertThat(objectUnderTest.hasNoGroups(), equalTo(false));
    }

    @Test
    void getOrCreateGroupForEvent_returns_expected_group_when_it_exists() {
        final Event event = mock(Event.class);
        final S3GroupIdentifier s3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(event)).thenReturn(s3GroupIdentifier);

        final Buffer buffer = mock(Buffer.class);
        when(bufferFactory.getBuffer(eq(s3Client), any(Supplier.class), any(Supplier.class)))
                .thenReturn(buffer);

        final S3GroupManager objectUnderTest = createObjectUnderTest();

        final S3Group result = objectUnderTest.getOrCreateGroupForEvent(event);

        assertThat(result, notNullValue());
        assertThat(result.getS3GroupIdentifier(), equalTo(s3GroupIdentifier));
        assertThat(result.getBuffer(), equalTo(buffer));

        final Event secondEvent = mock(Event.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(secondEvent)).thenReturn(s3GroupIdentifier);
        final S3Group secondResult = objectUnderTest.getOrCreateGroupForEvent(secondEvent);

        assertThat(secondResult,  notNullValue());
        assertThat(secondResult.getS3GroupIdentifier(), equalTo(s3GroupIdentifier));
        assertThat(secondResult.getBuffer(), equalTo(buffer));

        verify(bufferFactory, times(1)).getBuffer(eq(s3Client), any(Supplier.class), any(Supplier.class));

        final Collection<S3Group> groups = objectUnderTest.getS3GroupEntries();
        assertThat(groups, notNullValue());
        assertThat(groups.size(), equalTo(1));

        assertThat(groups.contains(result), equalTo(true));
        assertThat(groups.contains(secondResult), equalTo(true));
        assertThat(objectUnderTest.hasNoGroups(), equalTo(false));
    }

    @Test
    void recalculateAndGetGroupSize_returns_expected_size() {
        long bufferSizeBase = 100;
        long bufferSizeTotal = 100 + 200 + 300;

        final Event event = mock(Event.class);
        final S3GroupIdentifier s3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(event)).thenReturn(s3GroupIdentifier);

        final Buffer buffer = mock(Buffer.class);
        when(buffer.getSize()).thenReturn(bufferSizeBase);

        final Event secondEvent = mock(Event.class);
        final S3GroupIdentifier secondS3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(secondEvent)).thenReturn(secondS3GroupIdentifier);

        final Buffer secondBuffer = mock(Buffer.class);
        when(secondBuffer.getSize()).thenReturn(bufferSizeBase * 2);

        final Event thirdEvent = mock(Event.class);
        final S3GroupIdentifier thirdS3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(thirdEvent)).thenReturn(thirdS3GroupIdentifier);

        final Buffer thirdBuffer = mock(Buffer.class);
        when(thirdBuffer.getSize()).thenReturn(bufferSizeBase * 3);

        when(bufferFactory.getBuffer(eq(s3Client), any(Supplier.class), any(Supplier.class)))
                .thenReturn(buffer).thenReturn(secondBuffer).thenReturn(thirdBuffer);

        final S3GroupManager objectUnderTest = createObjectUnderTest();

        objectUnderTest.getOrCreateGroupForEvent(event);
        objectUnderTest.getOrCreateGroupForEvent(secondEvent);
        objectUnderTest.getOrCreateGroupForEvent(thirdEvent);

        final long totalGroupSize = objectUnderTest.recalculateAndGetGroupSize();

        assertThat(totalGroupSize, equalTo(bufferSizeTotal));
    }

    @Test
    void getGroupsOrderedBySize_returns_groups_in_expected_order() {

        long bufferSizeBase = 100;

        final Event event = mock(Event.class);
        final S3GroupIdentifier s3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(event)).thenReturn(s3GroupIdentifier);

        final Buffer buffer = mock(Buffer.class);
        when(buffer.getSize()).thenReturn(bufferSizeBase);

        final Event secondEvent = mock(Event.class);
        final S3GroupIdentifier secondS3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(secondEvent)).thenReturn(secondS3GroupIdentifier);

        final Buffer secondBuffer = mock(Buffer.class);
        when(secondBuffer.getSize()).thenReturn(bufferSizeBase * 2);

        final Event thirdEvent = mock(Event.class);
        final S3GroupIdentifier thirdS3GroupIdentifier = mock(S3GroupIdentifier.class);
        when(s3GroupIdentifierFactory.getS3GroupIdentifierForEvent(thirdEvent)).thenReturn(thirdS3GroupIdentifier);

        final Buffer thirdBuffer = mock(Buffer.class);
        when(thirdBuffer.getSize()).thenReturn(bufferSizeBase * 3);

        when(bufferFactory.getBuffer(eq(s3Client), any(Supplier.class), any(Supplier.class)))
                .thenReturn(buffer).thenReturn(secondBuffer).thenReturn(thirdBuffer);

        final S3GroupManager objectUnderTest = createObjectUnderTest();

        final S3Group firstGroup = objectUnderTest.getOrCreateGroupForEvent(event);
        final S3Group secondGroup = objectUnderTest.getOrCreateGroupForEvent(secondEvent);
        final S3Group thirdGroup = objectUnderTest.getOrCreateGroupForEvent(thirdEvent);

        assertThat(objectUnderTest.getNumberOfGroups(), equalTo(3));

        final Collection<S3Group> sortedGroups = objectUnderTest.getS3GroupsSortedBySize();

        assertThat(sortedGroups.size(), equalTo(3));
        assertThat(sortedGroups, contains(thirdGroup, secondGroup, firstGroup));

        objectUnderTest.removeGroup(secondGroup);

        final Collection<S3Group> sortedGroupsAfterRemoval = objectUnderTest.getS3GroupsSortedBySize();

        assertThat(sortedGroupsAfterRemoval.size(), equalTo(2));
        assertThat(objectUnderTest.getNumberOfGroups(), equalTo(2));
        assertThat(sortedGroupsAfterRemoval, contains(thirdGroup, firstGroup));
    }
}
