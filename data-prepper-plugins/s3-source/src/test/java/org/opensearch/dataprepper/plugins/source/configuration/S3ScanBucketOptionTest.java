/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.source.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class S3ScanBucketOptionTest {

    private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.USE_PLATFORM_LINE_BREAKS));
    @Test
    public void s3scan_bucket_options_with_scan_buckets_yaml_configuration_test() throws JsonProcessingException {
        final String bucketOptionsYaml = "              name: test-s3-source-test-output\n" +
                "              key_path:\n" +
                "                include:\n" +
                "                  - bucket2\n" +
                "                exclude:\n" +
                "                  - .jpeg\n" +
                "                  - .png\n" +
                "                  - .exe\n" +
                "                  - .json\n" +
                "                  - .parquet\n" +
                "                  - .snappy\n" +
                "                  - .gzip";
        final S3ScanBucketOption s3ScanBucketOption = objectMapper.readValue(bucketOptionsYaml, S3ScanBucketOption.class);
        assertThat(s3ScanBucketOption.getName(), equalTo("test-s3-source-test-output"));
        assertThat(s3ScanBucketOption.getKeyPath(), instanceOf(S3ScanKeyPathOption.class));
        assertThat(s3ScanBucketOption.getKeyPath().getS3scanIncludeOptions(),instanceOf(List.class));
        assertThat(s3ScanBucketOption.getKeyPath().getS3scanIncludeOptions().get(0),equalTo("bucket2"));
        assertThat(s3ScanBucketOption.getKeyPath().getS3ScanExcludeOptions(),instanceOf(List.class));
        assertThat(s3ScanBucketOption.getKeyPath().getS3ScanExcludeOptions().get(1),equalTo(".png"));
    }
}
