/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.peerforwarder;

import com.amazon.dataprepper.model.CheckpointState;
import com.amazon.dataprepper.model.buffer.SizeOverflowException;
import com.amazon.dataprepper.model.record.Record;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

class PeerForwarderReceiveBufferTest {
    private static final String TEST_PLUGIN_NAME = "test-plugin";
    private static final int TEST_BATCH_SIZE = 3;
    private static final int TEST_BUFFER_SIZE = 13;
    private static final int TEST_WRITE_TIMEOUT = 100;
    private static final int TEST_BATCH_READ_TIMEOUT = 5_000;

    PeerForwarderReceiveBuffer<Record<String>> createObjectUnderTest(final int bufferSize) {
        return new PeerForwarderReceiveBuffer<>(bufferSize, TEST_BATCH_SIZE, TEST_PLUGIN_NAME);
    }

    @Test
    void PeerForwarderReceiveBuffer_creation_test() {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
    }

    @Test
    void insert_null_should_throw_NullPointerException_test() {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        assertThrows(NullPointerException.class, () -> peerForwarderReceiveBuffer.write(null, TEST_WRITE_TIMEOUT));
    }

    @Test
    void testWriteAllSizeOverflow() {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        final Collection<Record<String>> testRecords = generateBatchRecords(TEST_BUFFER_SIZE + 1);
        assertThrows(SizeOverflowException.class, () -> peerForwarderReceiveBuffer.writeAll(testRecords, TEST_WRITE_TIMEOUT));
    }

    @Test
    void testNoEmptySpaceWriteOnly() throws TimeoutException {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(1);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        peerForwarderReceiveBuffer.write(new Record<>("FILL_THE_BUFFER"), TEST_WRITE_TIMEOUT);
        assertThrows(TimeoutException.class, () -> peerForwarderReceiveBuffer.write(new Record<>("TIMEOUT"), TEST_WRITE_TIMEOUT));
    }

    @Test
    void testNoAvailSpaceWriteAllOnly() throws Exception {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(2);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        final Collection<Record<String>> testRecords = generateBatchRecords(2);
        peerForwarderReceiveBuffer.write(new Record<>("FILL_THE_BUFFER"), TEST_WRITE_TIMEOUT);
        assertThrows(TimeoutException.class, () -> peerForwarderReceiveBuffer.writeAll(testRecords, TEST_WRITE_TIMEOUT));
    }

    @Test
    void testNoEmptySpaceAfterUncheckedRead() throws TimeoutException {
        // Given
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(1);
        peerForwarderReceiveBuffer.write(new Record<>("FILL_THE_BUFFER"), TEST_WRITE_TIMEOUT);

        // When
        peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);

        // Then
        final Record<String> timeoutRecord = new Record<>("TIMEOUT");
        assertThrows(TimeoutException.class, () -> peerForwarderReceiveBuffer.write(timeoutRecord, TEST_WRITE_TIMEOUT));
        assertThrows(TimeoutException.class, () ->
                peerForwarderReceiveBuffer.writeAll(Collections.singletonList(timeoutRecord), TEST_WRITE_TIMEOUT));
    }

    @Test
    void testWriteIntoEmptySpaceAfterCheckedRead() throws TimeoutException {
        // Given
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(1);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        peerForwarderReceiveBuffer.write(new Record<>("FILL_THE_BUFFER"), TEST_WRITE_TIMEOUT);

        // When
        final Map.Entry<Collection<Record<String>>, CheckpointState> readResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        peerForwarderReceiveBuffer.checkpoint(readResult.getValue());

        // Then
        peerForwarderReceiveBuffer.write(new Record<>("REFILL_THE_BUFFER"), TEST_WRITE_TIMEOUT);
        final Map.Entry<Collection<Record<String>>, CheckpointState> readCheckResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        assertThat(readResult.getKey().size(), equalTo(1));
    }

    @Test
    void testWriteAllIntoEmptySpaceAfterCheckedRead() throws Exception {
        // Given
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(2);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        final Collection<Record<String>> testRecords = generateBatchRecords(2);
        peerForwarderReceiveBuffer.writeAll(testRecords, TEST_WRITE_TIMEOUT);

        // When
        final Map.Entry<Collection<Record<String>>, CheckpointState> readResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        peerForwarderReceiveBuffer.checkpoint(readResult.getValue());

        // Then
        peerForwarderReceiveBuffer.writeAll(testRecords, TEST_WRITE_TIMEOUT);
        final Map.Entry<Collection<Record<String>>, CheckpointState> readCheckResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        assertThat(readResult.getKey().size(), equalTo(2));
    }

    @Test
    void testReadEmptyBuffer() {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        final Map.Entry<Collection<Record<String>>, CheckpointState> readResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        assertThat(readResult.getKey().size(), is(0));
    }

    @Test
    void testBatchRead() throws Exception {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);
        assertThat(peerForwarderReceiveBuffer, notNullValue());
        final int testSize = 5;

        final Collection<Record<String>> testRecords = generateBatchRecords(testSize);
        peerForwarderReceiveBuffer.writeAll(testRecords, TEST_WRITE_TIMEOUT);

        final Map.Entry<Collection<Record<String>>, CheckpointState> partialReadResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        final Collection<Record<String>> partialRecords = partialReadResult.getKey();
        final CheckpointState partialCheckpointState = partialReadResult.getValue();
        assertThat(partialRecords.size(), is(TEST_BATCH_SIZE));
        assertThat(partialCheckpointState.getNumRecordsToBeChecked(), is(TEST_BATCH_SIZE));

        final Map.Entry<Collection<Record<String>>, CheckpointState> finalReadResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        final Collection<Record<String>> finalBatch = finalReadResult.getKey();
        final CheckpointState finalCheckpointState = finalReadResult.getValue();
        assertThat(finalBatch.size(), is(testSize - TEST_BATCH_SIZE));
        assertThat(finalCheckpointState.getNumRecordsToBeChecked(), is(testSize - TEST_BATCH_SIZE));
    }

    @Test
    void testBufferIsEmpty_without_write() {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);

        Assertions.assertTrue(peerForwarderReceiveBuffer.isEmpty());
    }

    @Test
    void testBufferIsEmpty_after_read_and_checkpoint() throws TimeoutException {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);

        Record<String> record = new Record<>("TEST");
        peerForwarderReceiveBuffer.write(record, TEST_WRITE_TIMEOUT);

        Map.Entry<Collection<Record<String>>, CheckpointState> readResult = peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);
        peerForwarderReceiveBuffer.checkpoint(readResult.getValue());

        Assertions.assertTrue(peerForwarderReceiveBuffer.isEmpty());
    }

    @Test
    void testBufferIsNotEmpty() throws TimeoutException {
        final PeerForwarderReceiveBuffer<Record<String>> peerForwarderReceiveBuffer = createObjectUnderTest(TEST_BUFFER_SIZE);

        Record<String> record = new Record<>("TEST");
        peerForwarderReceiveBuffer.write(record, TEST_WRITE_TIMEOUT);

        peerForwarderReceiveBuffer.read(TEST_BATCH_READ_TIMEOUT);

        Assertions.assertFalse(peerForwarderReceiveBuffer.isEmpty());
    }

    private Collection<Record<String>> generateBatchRecords(final int numRecords) {
        final Collection<Record<String>> results = new ArrayList<>();
        for (int i = 0; i < numRecords; i++) {
            results.add(new Record<>(UUID.randomUUID().toString()));
        }
        return results;
    }
}
