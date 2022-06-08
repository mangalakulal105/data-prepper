/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.source;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.buffer.Buffer;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.record.Record;
import com.amazon.dataprepper.plugins.source.codec.Codec;
import io.micrometer.core.instrument.Counter;
import com.amazon.dataprepper.plugins.source.configuration.CompressionOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/**
 * Class responsible for taking an {@link S3ObjectReference} and creating all the necessary {@link Event}
 * objects in the Data Prepper {@link Buffer}.
 */
class S3ObjectWorker {
    private static final Logger LOG = LoggerFactory.getLogger(S3ObjectWorker.class);
    static final String S3_OBJECTS_FAILED_METRIC_NAME = "s3ObjectsFailed";

    private final S3Client s3Client;
    private final Buffer<Record<Event>> buffer;
    private final S3SourceConfig s3SourceConfig;
    private final Codec codec;
    private final Duration bufferTimeout;
    private final int numberOfRecordsToAccumulate;
    private final Counter s3ObjectsFailedCounter;

    public S3ObjectWorker(final S3Client s3Client,
                          final Buffer<Record<Event>> buffer,
                          final S3SourceConfig s3SourceConfig,
                          final Codec codec,
                          final Duration bufferTimeout,
                          final int numberOfRecordsToAccumulate,
                          final PluginMetrics pluginMetrics) {
        this.s3Client = s3Client;
        this.buffer = buffer;
        this.s3SourceConfig = s3SourceConfig;
        this.codec = codec;
        this.bufferTimeout = bufferTimeout;
        this.numberOfRecordsToAccumulate = numberOfRecordsToAccumulate;

        s3ObjectsFailedCounter = pluginMetrics.counter(S3_OBJECTS_FAILED_METRIC_NAME);
    }

    void parseS3Object(final S3ObjectReference s3ObjectReference) throws IOException {
        final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3ObjectReference.getBucketName())
                .key(s3ObjectReference.getKey())
                .build();

        final BufferAccumulator<Record<Event>> bufferAccumulator = BufferAccumulator.create(buffer, numberOfRecordsToAccumulate, bufferTimeout);

        try (final InputStream inputStream = getInputStreamFromCompressionType(getObjectRequest, s3SourceConfig.getCompression())) {
            codec.parse(inputStream, record -> {
                try {
                    bufferAccumulator.add(record);
                } catch (final Exception e) {
                    LOG.error("Failed writing S3 objects to buffer.", e);
                }
            });
        } catch (final Exception e) {
            LOG.error("Error reading from S3 object: s3ObjectReference={}.", s3ObjectReference, e);
            throw e;
        }

        try {
            bufferAccumulator.flush();
        } catch (final Exception e) {
            LOG.error("Failed writing S3 objects to buffer.", e);
        }
    }

    private InputStream getInputStreamFromCompressionType(GetObjectRequest getObjectRequest, CompressionOption compressionOption) throws IOException {
        final ResponseInputStream<GetObjectResponse> object = s3Client.getObject(getObjectRequest);
        if (compressionOption.equals(CompressionOption.NONE))
            return object;
        else if (compressionOption.equals(CompressionOption.GZIP))
            return new GZIPInputStream(object);
        else {
            if (getObjectRequest.key().endsWith(".gz")) {
                return new GZIPInputStream(object);
            }
            else {
                return object;
            }
        }
    }
}
