package org.opensearch.dataprepper.plugins.kafka.common.aws;

import org.opensearch.dataprepper.aws.api.AwsCredentialsOptions;
import org.opensearch.dataprepper.aws.api.AwsCredentialsSupplier;
import org.opensearch.dataprepper.plugins.kafka.configuration.AwsConfig;
import org.opensearch.dataprepper.plugins.kafka.configuration.AwsCredentialsConfig;
import org.opensearch.dataprepper.plugins.kafka.configuration.KafkaConnectionConfig;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.util.function.Supplier;

/**
 * Standard implementation in Kafka plugins to get an {@link AwsCredentialsProvider}.
 * The key interface this implements is {@link Supplier}, supplying an {@link AwsCredentialsProvider}.
 * In general, you can provide the {@link Supplier} into class; just use this class when
 * constructing.
 */
public class AwsContext {
    private final AwsConfig awsConfig;
    private final AwsCredentialsSupplier awsCredentialsSupplier;

    public AwsContext(KafkaConnectionConfig connectionConfig, AwsCredentialsSupplier awsCredentialsSupplier) {
        awsConfig = connectionConfig.getAwsConfig();
        this.awsCredentialsSupplier = awsCredentialsSupplier;
    }

    public AwsCredentialsProvider getOrDefault(AwsCredentialsConfig awsCredentialsConfig) {
        if(awsCredentialsConfig == null || awsCredentialsConfig.getStsRoleArn() == null) {
            return getDefault();
        }

        return getFromOptions(awsCredentialsConfig.toCredentialsOptions());
    }

    public Region getRegionOrDefault(AwsCredentialsConfig awsCredentialsConfig) {
        if(awsCredentialsConfig != null && awsCredentialsConfig.getRegion() != null) {
            return Region.of(awsCredentialsConfig.getRegion());
        }
        if(awsConfig != null && awsConfig.getRegion() != null) {
            return Region.of(awsConfig.getRegion());
        }
        return null;
    }

    private AwsCredentialsProvider getDefault() {
        final AwsCredentialsOptions credentialsOptions;
        if(awsConfig != null) {
            credentialsOptions = awsConfig.toCredentialsOptions();
        } else {
            credentialsOptions = AwsCredentialsOptions.defaultOptions();
        }

        return getFromOptions(credentialsOptions);
    }

    private AwsCredentialsProvider getFromOptions(AwsCredentialsOptions awsCredentialsOptions) {
        return awsCredentialsSupplier.getProvider(awsCredentialsOptions);
    }
}
