/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.aws;

import org.opensearch.dataprepper.aws.api.AwsCredentialsOptions;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class CredentialsCache {
    private final Map<CredentialsIdentifier, AwsCredentialsProvider> credentialsProviderMap;

    CredentialsCache() {
        credentialsProviderMap = new HashMap<>();
    }

    AwsCredentialsProvider getOrCreate(AwsCredentialsOptions awsCredentialsOptions, Supplier<AwsCredentialsProvider> providerSupplier) {
        final CredentialsIdentifier identifier = CredentialsIdentifier.fromAwsCredentialsOption(awsCredentialsOptions);

        return credentialsProviderMap.computeIfAbsent(identifier, i -> providerSupplier.get());
    }

    void putCredentialsProvider(AwsCredentialsOptions awsCredentialsOptions, AwsCredentialsProvider awsCredentialsProvider) {
        final CredentialsIdentifier identifier = CredentialsIdentifier.fromAwsCredentialsOption(awsCredentialsOptions);
        credentialsProviderMap.put(identifier, awsCredentialsProvider);
    }
}
