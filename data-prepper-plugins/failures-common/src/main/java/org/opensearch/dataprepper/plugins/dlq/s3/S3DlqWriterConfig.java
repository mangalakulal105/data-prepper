/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.dlq.s3;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * S3 Dlq Configuration.
 *
 * @since 2.2
 */
public class S3DlqWriterConfig {

    private static final int MAX_NUMBER_OF_RETRIES = 3;
    private static final String DEFAULT_AWS_REGION = "us-east-1";
    private static final String AWS_IAM_ROLE = "role";
    private static final String AWS_IAM = "iam";
    @JsonProperty("bucket")
    @NotNull
    @Size(min = 3, max = 63, message = "bucket lengthy should be between 3 and 63 characters")
    private String bucket;

    @JsonProperty("key_path_prefix")
    @Size(min = 1, max = 1024, message = "key_path_prefix length should be between 1 and 1024 characters")
    private String keyPathPrefix;

    @JsonProperty("region")
    @Size(min = 1, message = "region cannot be empty string")
    private String region = DEFAULT_AWS_REGION;

    @JsonProperty("sts_role_arn")
    @Size(min = 20, max = 2048, message = "sts_role_arn length should be between 1 and 2048 characters")
    private String stsRoleArn;

    @JsonProperty("sts_external_id")
    @Size(min = 2, max = 1224, message = "sts_external_id length should be between 2 and 1224 characters")
    private String stsExternalId;

    public String getBucket() {
        return bucket;
    }

    public String getKeyPathPrefix() {
        return keyPathPrefix;
    }

    public Region getRegion() {
        return Region.of(region);
    }

    private AwsCredentialsProvider getAwsCredentialsProvider() {

        if (stsRoleArn == null || stsRoleArn.isEmpty()) {
            return DefaultCredentialsProvider.create();
        }

        validateStsRoleArn();

        final StsClient stsClient = StsClient.builder()
            .region(getRegion())
            .build();

        AssumeRoleRequest.Builder assumeRoleRequestBuilder = AssumeRoleRequest.builder()
            .roleSessionName("s3-dlq-" + UUID.randomUUID())
            .roleArn(stsRoleArn);

        if (stsExternalId != null && !stsExternalId.isEmpty()) {
            assumeRoleRequestBuilder = assumeRoleRequestBuilder.externalId(stsExternalId);
        }

        return StsAssumeRoleCredentialsProvider.builder()
            .stsClient(stsClient)
            .refreshRequest(assumeRoleRequestBuilder.build())
            .build();
    }

    private void validateStsRoleArn() {
        final Arn arn = getArn();
        if (!AWS_IAM.equals(arn.service())) {
            throw new IllegalArgumentException("sts_role_arn must be an IAM Role");
        }
        final Optional<String> resourceType = arn.resource().resourceType();
        if (resourceType.isEmpty() || !resourceType.get().equals(AWS_IAM_ROLE)) {
            throw new IllegalArgumentException("sts_role_arn must be an IAM Role");
        }
    }

    private Arn getArn() {
        try {
            return Arn.fromString(stsRoleArn);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid ARN format for sts_role_arn");
        }
    }

    public S3Client getS3Client() {
        return S3Client.builder()
            .region(this.getRegion())
            .credentialsProvider(this.getAwsCredentialsProvider())
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(MAX_NUMBER_OF_RETRIES).build())
                .build())
            .build();
    }
}
