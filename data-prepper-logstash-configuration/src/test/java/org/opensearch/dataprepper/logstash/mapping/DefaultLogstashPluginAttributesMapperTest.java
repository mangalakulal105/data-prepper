/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.logstash.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.logstash.model.LogstashAttribute;
import org.opensearch.dataprepper.logstash.model.LogstashAttributeValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultLogstashPluginAttributesMapperTest {

    private String logstashAttributeName;
    private String value;
    private List<LogstashAttribute> logstashAttributes;
    private LogstashAttributeValue logstashAttributeValue;
    private LogstashAttributesMappings mappings;

    @BeforeEach
    void setUp() {
        value = UUID.randomUUID().toString();
        logstashAttributeName = UUID.randomUUID().toString();
        final LogstashAttribute logstashAttribute = mock(LogstashAttribute.class);
        logstashAttributeValue = mock(LogstashAttributeValue.class);
        when(logstashAttributeValue.getValue()).thenReturn(value);
        when(logstashAttribute.getAttributeName()).thenReturn(logstashAttributeName);
        when(logstashAttribute.getAttributeValue()).thenReturn(logstashAttributeValue);


        logstashAttributes = Collections.singletonList(logstashAttribute);
        mappings = mock(LogstashAttributesMappings.class);
    }

    private DefaultLogstashPluginAttributesMapper createObjectUnderTest() {
        return new DefaultLogstashPluginAttributesMapper();
    }

    @Test
    void mapAttributes_throws_with_null_Attributes() {
        final DefaultLogstashPluginAttributesMapper objectUnderTest = createObjectUnderTest();

        assertThrows(NullPointerException.class,
                () -> objectUnderTest.mapAttributes(null, mappings));
    }

    @Test
    void mapAttributes_throws_with_null_Mappings() {
        final DefaultLogstashPluginAttributesMapper objectUnderTest = createObjectUnderTest();

        assertThrows(NullPointerException.class,
                () -> objectUnderTest.mapAttributes(logstashAttributes, null));
    }

    @Test
    void mapAttributes_throws_with_null_Mappings_mappedAttributeNames() {
        final DefaultLogstashPluginAttributesMapper objectUnderTest = createObjectUnderTest();

        when(mappings.getMappedAttributeNames()).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> objectUnderTest.mapAttributes(logstashAttributes, mappings));
    }

    @Test
    void mapAttributes_throws_with_null_Mappings_additionalAttributes() {
        final DefaultLogstashPluginAttributesMapper objectUnderTest = createObjectUnderTest();

        when(mappings.getAdditionalAttributes()).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> objectUnderTest.mapAttributes(logstashAttributes, mappings));
    }

    @Test
    void mapAttributes_sets_mapped_attributes() {
        final String dataPrepperAttribute = UUID.randomUUID().toString();

        when(mappings.getMappedAttributeNames()).thenReturn(Collections.singletonMap(logstashAttributeName, dataPrepperAttribute));

        final Map<String, Object> actualPluginSettings =
                createObjectUnderTest().mapAttributes(logstashAttributes, mappings);

        assertThat(actualPluginSettings, notNullValue());
        assertThat(actualPluginSettings.size(), equalTo(1));
        assertThat(actualPluginSettings, hasKey(dataPrepperAttribute));
        assertThat(actualPluginSettings.get(dataPrepperAttribute), equalTo(value));
    }

    @Test
    void mapAttributes_sets_additional_attributes_to_those_values() {
        final String additionalAttributeName = UUID.randomUUID().toString();
        final String additionalAttributeValue = UUID.randomUUID().toString();
        when(mappings.getAdditionalAttributes()).thenReturn(Collections.singletonMap(additionalAttributeName, additionalAttributeValue));

        final Map<String, Object> actualPluginSettings =
                createObjectUnderTest().mapAttributes(logstashAttributes, mappings);

        assertThat(actualPluginSettings, notNullValue());
        assertThat(actualPluginSettings.size(), equalTo(1));
        assertThat(actualPluginSettings, hasKey(additionalAttributeName));
        assertThat(actualPluginSettings.get(additionalAttributeName), equalTo(additionalAttributeValue));
    }

    @Test
    void mapAttributes_with_negation_expression_negates_boolean_value() {
        final String dataPrepperAttribute = "!".concat(UUID.randomUUID().toString());
        boolean value = true;

        when(logstashAttributeValue.getValue()).thenReturn(value);
        when(mappings.getMappedAttributeNames()).thenReturn(Collections.singletonMap(logstashAttributeName, dataPrepperAttribute));

        final Map<String, Object> actualPluginSettings =
                createObjectUnderTest().mapAttributes(logstashAttributes, mappings);

        assertThat(actualPluginSettings, notNullValue());
        assertThat(actualPluginSettings.size(), equalTo(1));
        assertThat(actualPluginSettings.get(dataPrepperAttribute.substring(1)), equalTo(!value));
    }
}