/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.model.failures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class DlqObjectTest {

    private static final String ISO8601_FORMAT_STRING = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private String pluginId;
    private String pluginName;
    private String pipelineName;
    private Object failedData;

    @BeforeEach
    public void setUp() {
        pluginId = randomUUID().toString();
        pluginName = randomUUID().toString();
        pipelineName = randomUUID().toString();
        failedData = randomUUID();
    }

    @Test
    public void test_build_with_timestamp() {

        final DlqObject testObject = DlqObject.builder()
                .withPluginId(pluginId)
                .withPluginName(pluginName)
                .withPipelineName(pipelineName)
                .withFailedData(failedData)
                .withTimestamp(randomUUID().toString())
                .build();

        assertThat(testObject, is(notNullValue()));
    }

    @Test
    public void test_build_without_timestamp() {

        final DlqObject testObject = DlqObject.builder()
            .withPluginId(pluginId)
            .withPluginName(pluginName)
            .withPipelineName(pipelineName)
            .withFailedData(failedData)
            .build();

        assertThat(testObject, is(notNullValue()));
    }

    @Nested
    public class InvalidBuildParameters {

        private void createTestObject() {
            DlqObject.builder()
                .withPluginId(pluginId)
                .withPluginName(pluginName)
                .withPipelineName(pipelineName)
                .withFailedData(failedData)
                .build();
        }
        @Test
        public void test_invalid_pluginId() {
            pluginId = null;
            assertThrows(NullPointerException.class, this::createTestObject);
            pluginId = "";
            assertThrows(IllegalArgumentException.class, this::createTestObject);
        }

        @Test
        public void test_invalid_pluginName() {
            pluginName = null;
            assertThrows(NullPointerException.class, this::createTestObject);
            pluginName = "";
            assertThrows(IllegalArgumentException.class, this::createTestObject);
        }

        @Test
        public void test_invalid_pipelineName() {
            pipelineName = null;
            assertThrows(NullPointerException.class, this::createTestObject);
            pipelineName = "";
            assertThrows(IllegalArgumentException.class, this::createTestObject);
        }

        @Test
        public void test_invalid_failedData() {
            failedData = null;
            assertThrows(NullPointerException.class, this::createTestObject);
        }
    }

    @Nested
    class Getters {

        private DlqObject testObject;

        @BeforeEach
        public void setup() {

            testObject = DlqObject.builder()
                .withPluginId(pluginId)
                .withPluginName(pluginName)
                .withPipelineName(pipelineName)
                .withFailedData(failedData)
                .build();
        }

        @Test
        public void test_get_pluginId() {
            final String actualPluginId = testObject.getPluginId();
            assertThat(actualPluginId, is(notNullValue()));
            assertThat(actualPluginId, is(pluginId));
        }

        @Test
        public void test_get_pluginName() {
            final String actualPluginName = testObject.getPluginName();
            assertThat(actualPluginName, is(notNullValue()));
            assertThat(actualPluginName, is(pluginName));
        }

        @Test
        public void test_get_pipelineName() {
            final String actualPipelineName = testObject.getPipelineName();
            assertThat(actualPipelineName, is(notNullValue()));
            assertThat(actualPipelineName, is(pipelineName));
        }

        @Test
        public void test_get_failedData() {
            final Object actualFailedData = testObject.getFailedData();
            assertThat(actualFailedData, is(notNullValue()));
            assertThat(actualFailedData, is(failedData));
        }

        @Test
        public void test_get_timestamp() {
            final String string = testObject.getTimestamp();
            assertThat(string, is(notNullValue()));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(ISO8601_FORMAT_STRING);  // Specify locale to determine human language and cultural norms used in translating that input string.
            Instant actualTimestamp = LocalDateTime.parse(testObject.getTimestamp(), formatter)
                .atZone(ZoneId.systemDefault().normalized())
                .toInstant();

            assertThat(actualTimestamp, is(lessThanOrEqualTo(Instant.now())));
        }

        @Test
        public void test_get_version() {
            final String string = testObject.getVersion();
            assertThat(string, is(notNullValue()));
            assertThat(string, is(equalTo("1")));
        }
    }


    @Nested
    class EqualsAndHashCodeAndToString {

        private DlqObject testObject;

        @BeforeEach
        public void setup() {
            testObject = DlqObject.builder()
                .withPluginId(pluginId)
                .withPluginName(pluginName)
                .withPipelineName(pipelineName)
                .withFailedData(failedData)
                .build();
        }

        @Test
        void test_equals_returns_false_for_null() {
            assertThat(testObject.equals(null), is(equalTo(false)));
        }

        @Test
        void test_equals_returns_false_for_other_class() {
            assertThat(testObject.equals(randomUUID()), is(equalTo(false)));
        }

        @Test
        void test_equals_on_same_instance_returns_true() {
            assertThat(testObject.equals(testObject), is(equalTo(true)));
        }

        @Test
        void test_equals_a_clone_of_the_same_instance_returns_true() {

            final DlqObject otherTestObject = DlqObject.builder()
                .withPluginId(pluginId)
                .withPluginName(pluginName)
                .withPipelineName(pipelineName)
                .withFailedData(failedData)
                .withTimestamp(testObject.getTimestamp())
                .build();

            assertThat(testObject.equals(otherTestObject), is(equalTo(true)));
        }

        @Test
        void test_equals_returns_false_for_two_instances_with_different_values() {

            final ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);

            final Instant olderInstant = Instant.now().atZone(ZoneOffset.UTC)
                .withHour(now.getHour() -  1)
                .toInstant();

            final DlqObject otherTestObject = DlqObject.builder()
                .withPluginId(pluginId)
                .withPluginName(pluginName)
                .withPipelineName(pipelineName)
                .withFailedData(failedData)
                .withTimestamp(olderInstant)
                .build();

            assertThat(testObject, is(not(equalTo(otherTestObject))));
        }

        @Test
        void test_hash_codes_for_two_instances_have_different_values() {

            final ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);

            final Instant olderInstant = Instant.now().atZone(ZoneOffset.UTC)
                .withHour(now.getHour() -  1)
                .toInstant();

            final DlqObject otherTestObject = DlqObject.builder()
                .withPluginId(pluginId)
                .withPluginName(pluginName)
                .withPipelineName(pipelineName)
                .withFailedData(failedData)
                .withTimestamp(olderInstant)
                .build();

            assertThat(testObject.hashCode(), is(not(equalTo(otherTestObject.hashCode()))));
        }

        @Test
        void test_toString_has_all_values() {
            final String string = testObject.toString();

            assertThat(string, notNullValue());
            assertThat(string, allOf(
                containsString("DlqObject"),
                containsString(pluginId),
                containsString(pluginName),
                containsString(pipelineName),
                containsString(failedData.toString())
            ));
        }
    }
}
