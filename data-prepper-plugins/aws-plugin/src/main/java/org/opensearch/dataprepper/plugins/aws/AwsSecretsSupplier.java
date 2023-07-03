package org.opensearch.dataprepper.plugins.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opensearch.dataprepper.aws.api.SecretsSupplier;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.Map;
import java.util.stream.Collectors;

public class AwsSecretsSupplier implements SecretsSupplier {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final Map<String, Map<String, String>> secretConfigNameToKeyValuePairs;

    public AwsSecretsSupplier(final AwsSecretPluginConfig awsSecretPluginConfig) {
        secretConfigNameToKeyValuePairs = toKeyValuePairs(awsSecretPluginConfig);
    }

    private Map<String, Map<String, String>> toKeyValuePairs(final AwsSecretPluginConfig awsSecretPluginConfig) {
        final Map<String, SecretsManagerClient> secretsManagerClientMap = toSecretsManagerClientMap(
                awsSecretPluginConfig);
        final Map<String, AwsSecretManagerConfiguration> awsSecretManagerConfigurationMap = awsSecretPluginConfig
                .getAwsSecretManagerConfigurationMap();
        return secretsManagerClientMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    final String secretConfigurationId = entry.getKey();
                    final AwsSecretManagerConfiguration awsSecretManagerConfiguration =
                            awsSecretManagerConfigurationMap.get(secretConfigurationId);
                    final SecretsManagerClient secretsManagerClient = entry.getValue();
                    final GetSecretValueRequest getSecretValueRequest = awsSecretManagerConfiguration
                            .createGetSecretValueRequest();
                    final GetSecretValueResponse getSecretValueResponse;
                    try {
                        getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
                    } catch (Exception e) {
                        throw ResourceNotFoundException.builder()
                                .message(String.format("Unable to retrieve secret: %s",
                                        awsSecretManagerConfiguration.getAwsSecretName()))
                                .cause(e)
                                .build();
                    }

                    try {
                        return OBJECT_MAPPER.readValue(getSecretValueResponse.secretString(), MAP_TYPE_REFERENCE);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    private Map<String, SecretsManagerClient> toSecretsManagerClientMap(
            final AwsSecretPluginConfig awsSecretPluginConfig) {
        return awsSecretPluginConfig.getAwsSecretManagerConfigurationMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    final AwsSecretManagerConfiguration awsSecretManagerConfiguration = entry.getValue();
                    return awsSecretManagerConfiguration.createSecretManagerClient();
                }));
    }

    @Override
    public String retrieveValue(String secretConfigName, String key) {
        if (!secretConfigNameToKeyValuePairs.containsKey(secretConfigName)) {
            throw new IllegalArgumentException(String.format("Unable to find secretConfigName: %s", secretConfigName));
        }
        final Map<String, String> keyValuePairs = secretConfigNameToKeyValuePairs.get(secretConfigName);
        if (!keyValuePairs.containsKey(key)) {
            throw new IllegalArgumentException(String.format("Unable to find the value of key: %s under secretConfigName: %s",
                    key, secretConfigName));
        }
        return keyValuePairs.get(key);
    }
}
