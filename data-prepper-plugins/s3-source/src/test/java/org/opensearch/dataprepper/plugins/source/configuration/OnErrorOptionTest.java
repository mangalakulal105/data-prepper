/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.configuration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OnErrorOptionTest {
    @ParameterizedTest
    @EnumSource(OnErrorOption.class)
    void fromOptionValue(final OnErrorOption option) {
        assertThat(OnErrorOption.fromOptionValue(option.name().toLowerCase()), is(option));
    }

}