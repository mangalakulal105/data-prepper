/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.aggregate.actions;

import static org.opensearch.dataprepper.test.helper.ReflectivelySetField.setField;
import org.junit.jupiter.api.extension.ExtendWith; 
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

@ExtendWith(MockitoExtension.class)
public class PercentSamplerAggregateActionConfigTests {
    private PercentSamplerAggregateActionConfig percentSamplerAggregateActionConfig;

    private PercentSamplerAggregateActionConfig createObjectUnderTest() {
        return new PercentSamplerAggregateActionConfig();
    }
    
    @BeforeEach
    void setup() {
        percentSamplerAggregateActionConfig = createObjectUnderTest();
    }

    @Test
    void testValidConfig() throws NoSuchFieldException, IllegalAccessException {
        final double testPercent = ThreadLocalRandom.current().nextDouble(0.01, 99.9);
        setField(PercentSamplerAggregateActionConfig.class, percentSamplerAggregateActionConfig, "percent", testPercent);
        assertThat(percentSamplerAggregateActionConfig.getPercent(), equalTo(testPercent));
    }
    
    @Test
    void testInvalidConfig() throws NoSuchFieldException, IllegalAccessException {
        setField(PercentSamplerAggregateActionConfig.class, percentSamplerAggregateActionConfig, "percent", 0.0);
        assertThrows(IllegalArgumentException.class, () -> percentSamplerAggregateActionConfig.getPercent());
        setField(PercentSamplerAggregateActionConfig.class, percentSamplerAggregateActionConfig, "percent", 100.0);
        assertThrows(IllegalArgumentException.class, () -> percentSamplerAggregateActionConfig.getPercent());
        setField(PercentSamplerAggregateActionConfig.class, percentSamplerAggregateActionConfig, "percent", -1.0);
        assertThrows(IllegalArgumentException.class, () -> percentSamplerAggregateActionConfig.getPercent());
        setField(PercentSamplerAggregateActionConfig.class, percentSamplerAggregateActionConfig, "percent", 110.0);
        assertThrows(IllegalArgumentException.class, () -> percentSamplerAggregateActionConfig.getPercent());
    }
}
