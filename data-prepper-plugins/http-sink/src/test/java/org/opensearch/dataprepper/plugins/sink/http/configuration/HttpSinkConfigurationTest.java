/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.sink.http.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.model.types.ByteCount;
import org.opensearch.dataprepper.plugins.accumulator.BufferTypeOptions;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opensearch.dataprepper.plugins.sink.http.configuration.AuthTypeOptions.HTTP_BASIC;
import static org.opensearch.dataprepper.plugins.sink.http.configuration.AuthTypeOptions.UNAUTHENTICATED;

public class HttpSinkConfigurationTest {

    private static final String SINK_YAML =
            "        url: \"http://localhost:8080/test\"\n" +
            "        proxy: test-proxy\n" +
            "        codec:\n" +
            "          ndjson:\n" +
            "        http_method: \"POST\"\n" +
            "        auth_type: \"http_basic\"\n" +
            "        authentication:\n" +
            "          http_basic:\n" +
            "            username: \"username\"\n" +
            "            password: \"vip\"\n" +
            "          bearer_token:\n" +
            "            token: \"\"\n" +
            "        ssl: false\n" +
            "        dlq_file: \"/your/local/dlq-file\"\n" +
            "        dlq:\n" +
            "        ssl_certificate_file: \"/full/path/to/certfile.crt\"\n" +
            "        ssl_key_file: \"/full/path/to/keyfile.key\"\n" +
            "        buffer_type: \"in_memory\"\n" +
            "        aws:\n" +
            "          region: \"us-east-2\"\n" +
            "          sts_role_arn: \"arn:aws:iam::895099425785:role/data-prepper-s3source-execution-role\"\n" +
            "          sts_external_id: \"test-external-id\"\n" +
            "          sts_header_overrides: {\"test\": test }\n" +
            "        threshold:\n" +
            "          event_count: 2000\n" +
            "          maximum_size: 2mb\n" +
            "        max_retries: 5\n" +
            "        aws_sigv4: true\n" +
            "        custom_header:\n" +
            "          X-Amzn-SageMaker-Custom-Attributes: [\"test-attribute\"]\n" +
            "          X-Amzn-SageMaker-Target-Model: [\"test-target-model\"]\n" +
            "          X-Amzn-SageMaker-Target-Variant: [\"test-target-variant\"]\n" +
            "          X-Amzn-SageMaker-Target-Container-Hostname: [\"test-container-host\"]\n" +
            "          X-Amzn-SageMaker-Inference-Id: [\"test-interface-id\"]\n" +
            "          X-Amzn-SageMaker-Enable-Explanations: [\"test-explanation\"]";

    private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.USE_PLATFORM_LINE_BREAKS));


    @Test
    void default_worker_test() {
        assertThat(new HttpSinkConfiguration().getWorkers(), CoreMatchers.equalTo(1));
    }

    @Test
    void default_codec_test() {
        assertNull(new HttpSinkConfiguration().getCodec());
    }

    @Test
    void default_proxy_test() {
        assertNull(new HttpSinkConfiguration().getProxy());
    }

    @Test
    void default_http_method_test() {
        assertThat(new HttpSinkConfiguration().getHttpMethod(), CoreMatchers.equalTo(HTTPMethodOptions.POST));
    }

    @Test
    void default_auth_type_test() {
        assertThat(new HttpSinkConfiguration().getAuthType(), equalTo(UNAUTHENTICATED));
    }

    @Test
    void get_url_test() {
        assertThat(new HttpSinkConfiguration().getUrl(), equalTo(null));
    }

    @Test
    void get_authentication_test() {
        assertNull(new HttpSinkConfiguration().getAuthentication());
    }

    @Test
    void default_ssl_test() {
        assertThat(new HttpSinkConfiguration().isSsl(), equalTo(false));
    }

    @Test
    void default_awsSigv4_test() {
        assertThat(new HttpSinkConfiguration().isAwsSigv4(), equalTo(false));
    }

    @Test
    void get_ssl_certificate_file_test() {
        assertNull(new HttpSinkConfiguration().getSslCertificateFile());
    }

    @Test
    void get_ssl_key_file_test() {
        assertNull(new HttpSinkConfiguration().getSslKeyFile());
    }

    @Test
    void default_buffer_type_test() {
        assertThat(new HttpSinkConfiguration().getBufferType(), equalTo(BufferTypeOptions.INMEMORY));
    }

    @Test
    void get_threshold_options_test() {
        assertNull(new HttpSinkConfiguration().getThresholdOptions());
    }

    @Test
    void default_max_upload_retries_test() {
        assertThat(new HttpSinkConfiguration().getMaxUploadRetries(), equalTo(5));
    }

    @Test
    void get_aws_authentication_options_test() {
        assertNull(new HttpSinkConfiguration().getAwsAuthenticationOptions());
    }

    @Test
    void get_custom_header_options_test() {
        assertNull(new HttpSinkConfiguration().getCustomHeaderOptions());
    }

    @Test
    void http_sink_pipeline_test_with_provided_config_options() throws JsonProcessingException {
        final HttpSinkConfiguration httpSinkConfiguration = objectMapper.readValue(SINK_YAML, HttpSinkConfiguration.class);

        assertThat(httpSinkConfiguration.getUrl(),equalTo("http://localhost:8080/test"));
        assertThat(httpSinkConfiguration.getHttpMethod(),equalTo(HTTPMethodOptions.POST));
        assertThat(httpSinkConfiguration.getAuthType(),equalTo(HTTP_BASIC));
        assertThat(httpSinkConfiguration.getBufferType(),equalTo(BufferTypeOptions.INMEMORY));
        assertThat(httpSinkConfiguration.getMaxUploadRetries(),equalTo(5));
        assertThat(httpSinkConfiguration.getProxy(),equalTo("test-proxy"));
        assertThat(httpSinkConfiguration.getSslCertificateFile(),equalTo("/full/path/to/certfile.crt"));
        assertThat(httpSinkConfiguration.getSslKeyFile(),equalTo("/full/path/to/keyfile.key"));
        assertThat(httpSinkConfiguration.getWorkers(),equalTo(1));
        assertThat(httpSinkConfiguration.getDlqFile(),equalTo("/your/local/dlq-file"));

        final Map<String, List<String>> customHeaderOptions = httpSinkConfiguration.getCustomHeaderOptions();
        assertThat(customHeaderOptions.get("X-Amzn-SageMaker-Custom-Attributes"),equalTo(List.of("test-attribute")));
        assertThat(customHeaderOptions.get("X-Amzn-SageMaker-Inference-Id"),equalTo(List.of("test-interface-id")));
        assertThat(customHeaderOptions.get("X-Amzn-SageMaker-Enable-Explanations"),equalTo(List.of("test-explanation")));
        assertThat(customHeaderOptions.get("X-Amzn-SageMaker-Target-Variant"),equalTo(List.of("test-target-variant")));
        assertThat(customHeaderOptions.get("X-Amzn-SageMaker-Target-Container-Hostname"),equalTo(List.of("test-container-host")));
        assertThat(customHeaderOptions.get("X-Amzn-SageMaker-Target-Model"),equalTo(List.of("test-target-model")));

        final AwsAuthenticationOptions awsAuthenticationOptions =
                httpSinkConfiguration.getAwsAuthenticationOptions();

        assertThat(awsAuthenticationOptions.getAwsRegion(),equalTo(Region.US_EAST_2));
        assertThat(awsAuthenticationOptions.getAwsStsExternalId(),equalTo("test-external-id"));
        assertThat(awsAuthenticationOptions.getAwsStsHeaderOverrides().get("test"),equalTo("test"));
        assertThat(awsAuthenticationOptions.getAwsStsRoleArn(),equalTo("arn:aws:iam::895099425785:role/data-prepper-s3source-execution-role"));

        final ThresholdOptions thresholdOptions = httpSinkConfiguration.getThresholdOptions();
        assertThat(thresholdOptions.getEventCount(),equalTo(2000));
        assertThat(thresholdOptions.getMaximumSize(),instanceOf(ByteCount.class));
    }
}
