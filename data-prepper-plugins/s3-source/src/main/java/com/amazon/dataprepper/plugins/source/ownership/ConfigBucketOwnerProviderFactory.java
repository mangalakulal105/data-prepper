/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugins.source.ownership;

import com.amazon.dataprepper.plugins.source.S3SourceConfig;
import com.amazon.dataprepper.plugins.source.SqsQueueUrl;

import java.net.MalformedURLException;

public class ConfigBucketOwnerProviderFactory {
    public BucketOwnerProvider createBucketOwnerProvider(final S3SourceConfig s3SourceConfig) {
        if(s3SourceConfig.isDisableBucketOwnershipValidation())
            return new NoOwnershipBucketOwnerProvider();

        final String queueUrl = s3SourceConfig.getSqsOptions().getSqsUrl();
        final String accountId;
        try {
            accountId = SqsQueueUrl.parse(queueUrl).getAccountId();
        } catch (final MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return new StaticBucketOwnerProvider(accountId);
    }
}
