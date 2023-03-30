/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.dlq.plugins.s3;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import org.opensearch.dataprepper.dlq.DlqWriter;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.configuration.DataPrepperVersion;
import org.opensearch.dataprepper.model.failures.DlqObject;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S3 Dlq writer. Stores DLQ Objects in an S3 bucket.
 *
 * @since 2.2
 */
public class S3DlqWriter implements DlqWriter {

    static final String S3_DLQ_RECORDS_SUCCESS = "dlqS3RecordsSuccess";
    static final String S3_DLQ_RECORDS_FAILED = "dlqS3RecordsFailed";
    static final String S3_DLQ_REQUEST_SUCCESS = "dlqS3RequestSuccess";
    static final String S3_DLQ_REQUEST_FAILED = "dlqS3RequestFailed";
    static final String S3_DLQ_REQUEST_LATENCY = "dlqS3RequestLatency";
    static final String S3_DLQ_REQUEST_SIZE_BYTES = "dlqS3RequestSizeBytes";
    private static final String KEY_NAME_FORMAT = "dlq-v%s-%s-%s-%s-%s";
    private static final String FULL_KEY_FORMAT = "%s/%s";

    private final S3Client s3Client;
    private final String bucket;
    private final String keyPathPrefix;
    private final ObjectMapper objectMapper;

    private final Counter dlqS3RecordsSuccessCounter;
    private final Counter dlqS3RecordsFailedCounter;
    private final Counter dlqS3RequestSuccessCounter;
    private final Counter dlqS3RequestFailedCounter;
    private final Timer dlqS3RequestTimer;
    private final DistributionSummary dlqS3RequestSizeBytesSummary;

    S3DlqWriter(final S3DlqWriterConfig s3DlqWriterConfig, final ObjectMapper objectMapper, final PluginMetrics pluginMetrics) {
        dlqS3RecordsSuccessCounter = pluginMetrics.counter(S3_DLQ_RECORDS_SUCCESS);
        dlqS3RecordsFailedCounter = pluginMetrics.counter(S3_DLQ_RECORDS_FAILED);
        dlqS3RequestSuccessCounter = pluginMetrics.counter(S3_DLQ_REQUEST_SUCCESS);
        dlqS3RequestFailedCounter = pluginMetrics.counter(S3_DLQ_REQUEST_FAILED);
        dlqS3RequestTimer = pluginMetrics.timer(S3_DLQ_REQUEST_LATENCY);
        dlqS3RequestSizeBytesSummary = pluginMetrics.summary(S3_DLQ_REQUEST_SIZE_BYTES);

        this.s3Client = s3DlqWriterConfig.getS3Client();
        Objects.requireNonNull(s3DlqWriterConfig.getBucket());
        this.bucket = s3DlqWriterConfig.getBucket();
        this.keyPathPrefix = s3DlqWriterConfig.getKeyPathPrefix();
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(final List<DlqObject> dlqObjects, final String pipelineName, final String pluginId) throws IOException {
        try {
            doWrite(dlqObjects, pipelineName, pluginId);
            dlqS3RequestSuccessCounter.increment();
            dlqS3RecordsSuccessCounter.increment(dlqObjects.size());
        } catch (final Exception e) {
            dlqS3RequestFailedCounter.increment();
            dlqS3RecordsFailedCounter.increment(dlqObjects.size());
            throw e;
        }
    }

    private void doWrite(final List<DlqObject> dlqObjects, final String pipelineName, final String pluginId) throws IOException {
        final PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(buildKey(pipelineName, pluginId))
            .build();

        final String content = deserialize(dlqObjects);

        final PutObjectResponse response = timedPutObject(putObjectRequest, content);

        if (!response.sdkHttpResponse().isSuccessful()) {
            throw new IOException(String.format(
                "Failed to write to S3 dlq: [%s] to S3 due to status code: %d",
                content, response.sdkHttpResponse().statusCode()));
        }
    }

    private PutObjectResponse timedPutObject(final PutObjectRequest putObjectRequest, final String content) throws IOException {
        try {
            return dlqS3RequestTimer.recordCallable(() -> putObject(putObjectRequest, content));
        } catch (final Exception ex) {
            throw new IOException(String.format("Failed to write to S3 dlq: [%s] to S3.", content), ex);
        }
    }

    private PutObjectResponse putObject(final PutObjectRequest request, final String content) throws IOException {
        try {
            return s3Client.putObject(request, RequestBody.fromString(content));
        } catch (Exception ex) {
            throw new IOException(String.format("Failed to write to S3 dlq: [%s] to S3.", content), ex);
        }
    }

    private String deserialize(final List<DlqObject> dlqObjects) throws IOException {
        try {
            final String content = objectMapper.writeValueAsString(dlqObjects);

            dlqS3RequestSizeBytesSummary.record(content.getBytes(StandardCharsets.UTF_8).length);

            return content;
        } catch (JsonProcessingException e) {
            throw new IOException(String.format("Failed to build valid S3 request"));
        }
    }

    private String buildKey(final String pipelineName, final String pluginId) {
        final String key = String.format(KEY_NAME_FORMAT, DataPrepperVersion.getCurrentVersion().getMajorVersion(),
            pipelineName, pluginId, Instant.now(), UUID.randomUUID());
        return keyPathPrefix == null ? key : String.format(FULL_KEY_FORMAT, keyPathPrefix, key);
    }

    @Override
    public void close() throws IOException {
        s3Client.close();
    }
}
