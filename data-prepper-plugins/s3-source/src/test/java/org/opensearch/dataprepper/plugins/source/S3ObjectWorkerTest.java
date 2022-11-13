/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.buffer.Buffer;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.source.codec.Codec;
import org.opensearch.dataprepper.plugins.source.compression.CompressionEngine;
import org.opensearch.dataprepper.plugins.source.ownership.BucketOwnerProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ObjectWorkerTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private Buffer<Record<Event>> buffer;

    @Mock
    private CompressionEngine compressionEngine;

    @Mock
    private Codec codec;

    @Mock
    private BucketOwnerProvider bucketOwnerProvider;

    private Duration bufferTimeout;
    private int recordsToAccumulate;

    @Mock
    private S3ObjectReference s3ObjectReference;

    @Mock
    private PluginMetrics pluginMetrics;
    @Mock
    private Counter s3ObjectsFailedCounter;
    @Mock
    private Counter s3ObjectsSucceededCounter;
    @Mock
    private Timer s3ObjectReadTimer;
    @Mock
    private BiConsumer<Event, S3ObjectReference> eventConsumer;

    private String bucketName;
    private String key;
    private ResponseInputStream<GetObjectResponse> objectInputStream;

    private Exception exceptionThrownByCallable;

    @BeforeEach
    void setUp() throws Exception {
        final Random random = new Random();
        bufferTimeout = Duration.ofMillis(random.nextInt(100) + 100);
        recordsToAccumulate = random.nextInt(10) + 2;

        bucketName = UUID.randomUUID().toString();
        key = UUID.randomUUID().toString();
        when(s3ObjectReference.getBucketName()).thenReturn(bucketName);
        when(s3ObjectReference.getKey()).thenReturn(key);

        exceptionThrownByCallable = null;
        when(s3ObjectReadTimer.recordCallable(any(Callable.class)))
                .thenAnswer(a -> {
                    try {
                        a.getArgument(0, Callable.class).call();
                    } catch (final Exception ex) {
                        exceptionThrownByCallable = ex;
                        throw ex;
                    }
                    return null;
                });

        when(pluginMetrics.counter(S3ObjectWorker.S3_OBJECTS_FAILED_METRIC_NAME)).thenReturn(s3ObjectsFailedCounter);
        when(pluginMetrics.counter(S3ObjectWorker.S3_OBJECTS_SUCCEEDED_METRIC_NAME)).thenReturn(s3ObjectsSucceededCounter);
        when(pluginMetrics.timer(S3ObjectWorker.S3_OBJECTS_TIME_ELAPSED_METRIC_NAME)).thenReturn(s3ObjectReadTimer);

        objectInputStream = mock(ResponseInputStream.class);
    }

    private S3ObjectWorker createObjectUnderTest() {
        return new S3ObjectWorker(s3Client, buffer, compressionEngine, codec, bucketOwnerProvider, bufferTimeout, recordsToAccumulate, eventConsumer, pluginMetrics);
    }

    @Test
    void parseS3Object_calls_getObject_with_correct_GetObjectRequest() throws IOException {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);

        createObjectUnderTest().parseS3Object(s3ObjectReference);

        final ArgumentCaptor<GetObjectRequest> getObjectRequestArgumentCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(getObjectRequestArgumentCaptor.capture());

        final GetObjectRequest actualGetObjectRequest = getObjectRequestArgumentCaptor.getValue();

        assertThat(actualGetObjectRequest, notNullValue());
        assertThat(actualGetObjectRequest.bucket(), equalTo(bucketName));
        assertThat(actualGetObjectRequest.key(), equalTo(key));
        assertThat(actualGetObjectRequest.expectedBucketOwner(), nullValue());
    }

    @Test
    void parseS3Object_calls_getObject_with_correct_GetObjectRequest_when_bucketOwner_is_present() throws IOException {
        final ResponseInputStream<GetObjectResponse> objectInputStream = mock(ResponseInputStream.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);

        final String bucketOwner = UUID.randomUUID().toString();
        when(bucketOwnerProvider.getBucketOwner(bucketName)).thenReturn(Optional.of(bucketOwner));

        createObjectUnderTest().parseS3Object(s3ObjectReference);

        final ArgumentCaptor<GetObjectRequest> getObjectRequestArgumentCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(getObjectRequestArgumentCaptor.capture());

        final GetObjectRequest actualGetObjectRequest = getObjectRequestArgumentCaptor.getValue();

        assertThat(actualGetObjectRequest, notNullValue());
        assertThat(actualGetObjectRequest.bucket(), equalTo(bucketName));
        assertThat(actualGetObjectRequest.key(), equalTo(key));
        assertThat(actualGetObjectRequest.expectedBucketOwner(), equalTo(bucketOwner));
    }

    @Test
    void parseS3Object_calls_Codec_parse_on_S3InputStream() throws Exception {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);
        when(compressionEngine.createInputStream(key, objectInputStream)).thenReturn(objectInputStream);

        createObjectUnderTest().parseS3Object(s3ObjectReference);

        verify(codec).parse(eq(objectInputStream), any(Consumer.class));
    }

    @Test
    void parseS3Object_calls_Codec_parse_with_Consumer_that_adds_to_BufferAccumulator() throws Exception {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);
        when(compressionEngine.createInputStream(key, objectInputStream)).thenReturn(objectInputStream);

        final BufferAccumulator bufferAccumulator = mock(BufferAccumulator.class);
        try (final MockedStatic<BufferAccumulator> bufferAccumulatorMockedStatic = mockStatic(BufferAccumulator.class)) {
            bufferAccumulatorMockedStatic.when(() -> BufferAccumulator.create(buffer, recordsToAccumulate, bufferTimeout))
                    .thenReturn(bufferAccumulator);
            createObjectUnderTest().parseS3Object(s3ObjectReference);
        }

        final ArgumentCaptor<Consumer<Record<Event>>> eventConsumerArgumentCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(codec).parse(any(InputStream.class), eventConsumerArgumentCaptor.capture());

        final Consumer<Record<Event>> consumerUnderTest = eventConsumerArgumentCaptor.getValue();

        final Record<Event> record = mock(Record.class);
        final Event event = mock(Event.class);
        when(record.getData()).thenReturn(event);

        consumerUnderTest.accept(record);

        final InOrder inOrder = inOrder(eventConsumer, bufferAccumulator);
        inOrder.verify(eventConsumer).accept(event, s3ObjectReference);
        inOrder.verify(bufferAccumulator).add(record);
    }

    @Test
    void parseS3Object_calls_BufferAccumulator_flush_after_Codec_parse() throws Exception {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);
        when(compressionEngine.createInputStream(key, objectInputStream)).thenReturn(objectInputStream);

        final BufferAccumulator bufferAccumulator = mock(BufferAccumulator.class);
        try (final MockedStatic<BufferAccumulator> bufferAccumulatorMockedStatic = mockStatic(BufferAccumulator.class)) {
            bufferAccumulatorMockedStatic.when(() -> BufferAccumulator.create(buffer, recordsToAccumulate, bufferTimeout))
                    .thenReturn(bufferAccumulator);
            createObjectUnderTest().parseS3Object(s3ObjectReference);
        }

        final InOrder inOrder = inOrder(codec, bufferAccumulator);

        inOrder.verify(codec).parse(any(InputStream.class), any(Consumer.class));
        inOrder.verify(bufferAccumulator).flush();
    }

    @Test
    void parseS3Object_increments_success_counter_after_parsing_S3_object() throws IOException {
        final ResponseInputStream<GetObjectResponse> objectInputStream = mock(ResponseInputStream.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);

        final S3ObjectWorker objectUnderTest = createObjectUnderTest();
        objectUnderTest.parseS3Object(s3ObjectReference);

        verify(s3ObjectsSucceededCounter).increment();
        verifyNoInteractions(s3ObjectsFailedCounter);
        assertThat(exceptionThrownByCallable, nullValue());
    }

    @Test
    void parseS3Object_throws_Exception_and_increments_counter_when_unable_to_get_S3_object() {
        final RuntimeException expectedException = mock(RuntimeException.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(expectedException);

        final S3ObjectWorker objectUnderTest = createObjectUnderTest();
        final RuntimeException actualException = assertThrows(RuntimeException.class, () -> objectUnderTest.parseS3Object(s3ObjectReference));

        assertThat(actualException, sameInstance(expectedException));

        verify(s3ObjectsFailedCounter).increment();
        verifyNoInteractions(s3ObjectsSucceededCounter);
        assertThat(exceptionThrownByCallable, sameInstance(expectedException));
    }

    @Test
    void parseS3Object_throws_Exception_and_increments_failure_counter_when_unable_to_parse_S3_object() throws IOException {
        when(compressionEngine.createInputStream(key, objectInputStream)).thenReturn(objectInputStream);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);
        final IOException expectedException = mock(IOException.class);
        doThrow(expectedException)
                .when(codec).parse(any(InputStream.class), any(Consumer.class));

        final S3ObjectWorker objectUnderTest = createObjectUnderTest();
        final IOException actualException = assertThrows(IOException.class, () -> objectUnderTest.parseS3Object(s3ObjectReference));

        assertThat(actualException, sameInstance(expectedException));

        verify(s3ObjectsFailedCounter).increment();
        verifyNoInteractions(s3ObjectsSucceededCounter);
        assertThat(exceptionThrownByCallable, sameInstance(expectedException));
    }

    @Test
    void parseS3Object_throws_Exception_and_increments_failure_counter_when_unable_to_GetObject_from_S3() {
        final RuntimeException expectedException = mock(RuntimeException.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(expectedException);

        final S3ObjectWorker objectUnderTest = createObjectUnderTest();
        final RuntimeException actualException = assertThrows(RuntimeException.class, () -> objectUnderTest.parseS3Object(s3ObjectReference));

        assertThat(actualException, sameInstance(expectedException));

        verify(s3ObjectsFailedCounter).increment();
        verifyNoInteractions(s3ObjectsSucceededCounter);
        assertThat(exceptionThrownByCallable, sameInstance(expectedException));
    }

    @Test
    void parseS3Object_throws_Exception_and_increments_failure_counter_when_CompressionEngine_fails() throws IOException {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);
        final IOException expectedException = mock(IOException.class);
        when(compressionEngine.createInputStream(key, objectInputStream)).thenThrow(expectedException);

        final S3ObjectWorker objectUnderTest = createObjectUnderTest();
        final IOException actualException = assertThrows(IOException.class, () -> objectUnderTest.parseS3Object(s3ObjectReference));

        assertThat(actualException, sameInstance(expectedException));

        verify(s3ObjectsFailedCounter).increment();
        verifyNoInteractions(s3ObjectsSucceededCounter);
        assertThat(exceptionThrownByCallable, sameInstance(expectedException));
    }

    @Test
    void parseS3Object_calls_GetObject_after_Callable() throws Exception {
        final ResponseInputStream<GetObjectResponse> objectInputStream = mock(ResponseInputStream.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectInputStream);

        final S3ObjectWorker objectUnderTest = createObjectUnderTest();
        objectUnderTest.parseS3Object(s3ObjectReference);

        final InOrder inOrder = inOrder(s3ObjectReadTimer, s3Client);

        inOrder.verify(s3ObjectReadTimer).recordCallable(any(Callable.class));
        inOrder.verify(s3Client).getObject(any(GetObjectRequest.class));
    }
}