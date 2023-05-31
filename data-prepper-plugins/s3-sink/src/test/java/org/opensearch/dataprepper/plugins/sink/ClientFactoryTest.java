/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.aws.api.AwsCredentialsOptions;
import org.opensearch.dataprepper.aws.api.AwsCredentialsSupplier;
import org.opensearch.dataprepper.plugins.sink.configuration.AwsAuthenticationOptions;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientFactoryTest {
    @Mock
    private S3SinkConfig s3SinkConfig;
    @Mock
    private AwsCredentialsSupplier awsCredentialsSupplier;

    @Mock
    private AwsAuthenticationOptions awsAuthenticationOptions;

    @BeforeEach
    void setUp() {
        when(s3SinkConfig.getAwsAuthenticationOptions()).thenReturn(awsAuthenticationOptions);
    }

    @Test
    void createS3Client_with_real_S3Client() {
        final S3Client s3Client = ClientFactory.createS3Client(s3SinkConfig, awsCredentialsSupplier);

        assertThat(s3Client, notNullValue());
    }

    @Test
    void createS3Client_provides_correct_inputs() {
        Region region = Region.US_WEST_2;
        String stsRoleArn = UUID.randomUUID().toString();
        final Map<String, String> stsHeaderOverrides = Map.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        when(awsAuthenticationOptions.getAwsRegion()).thenReturn(region);
        when(awsAuthenticationOptions.getAwsStsRoleArn()).thenReturn(stsRoleArn);
        when(awsAuthenticationOptions.getAwsStsHeaderOverrides()).thenReturn(stsHeaderOverrides);

        AwsCredentialsProvider expectedCredentialsProvider = mock(AwsCredentialsProvider.class);
        when(awsCredentialsSupplier.getProvider(any())).thenReturn(expectedCredentialsProvider);

        S3ClientBuilder s3ClientBuilder = mock(S3ClientBuilder.class);
        when(s3ClientBuilder.region(region)).thenReturn(s3ClientBuilder);
        when(s3ClientBuilder.credentialsProvider(any())).thenReturn(s3ClientBuilder);
        when(s3ClientBuilder.overrideConfiguration(any(ClientOverrideConfiguration.class))).thenReturn(s3ClientBuilder);
        try(MockedStatic<S3Client> s3ClientMockedStatic = mockStatic(S3Client.class)) {
            s3ClientMockedStatic.when(S3Client::builder)
                    .thenReturn(s3ClientBuilder);
            ClientFactory.createS3Client(s3SinkConfig, awsCredentialsSupplier);
        }

        ArgumentCaptor<AwsCredentialsProvider> credentialsProviderArgumentCaptor = ArgumentCaptor.forClass(AwsCredentialsProvider.class);
        verify(s3ClientBuilder).credentialsProvider(credentialsProviderArgumentCaptor.capture());

        final AwsCredentialsProvider actualCredentialsProvider = credentialsProviderArgumentCaptor.getValue();

        assertThat(actualCredentialsProvider, equalTo(expectedCredentialsProvider));

        ArgumentCaptor<AwsCredentialsOptions> optionsArgumentCaptor = ArgumentCaptor.forClass(AwsCredentialsOptions.class);
        verify(awsCredentialsSupplier).getProvider(optionsArgumentCaptor.capture());

        final AwsCredentialsOptions actualCredentialsOptions = optionsArgumentCaptor.getValue();
        assertThat(actualCredentialsOptions.getRegion(), equalTo(region));
        assertThat(actualCredentialsOptions.getStsRoleArn(), equalTo(stsRoleArn));
        assertThat(actualCredentialsOptions.getStsHeaderOverrides(), equalTo(stsHeaderOverrides));
    }
}