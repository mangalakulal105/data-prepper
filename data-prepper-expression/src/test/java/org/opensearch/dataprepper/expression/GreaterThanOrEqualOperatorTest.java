/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.expression.antlr.DataPrepperExpressionParser;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GreaterThanOrEqualOperatorTest {
    final GreaterThanOrEqualOperator objectUnderTest = new GreaterThanOrEqualOperator();

    @Test
    void testGetSymbol() {
        assertThat(objectUnderTest.getSymbol(), is(DataPrepperExpressionParser.GTE));
    }

    @Test
    void testEvalValidArgs() {
        assertThat(objectUnderTest.eval(2, 1), is(true));
        assertThat(objectUnderTest.eval(1, 2), is(false));
        assertThat(objectUnderTest.eval(1, 1), is(true));
        assertThat(objectUnderTest.eval(2f, 1), is(true));
        assertThat(objectUnderTest.eval(1f, 2), is(false));
        assertThat(objectUnderTest.eval(1f, 1), is(true));
        assertThat(objectUnderTest.eval(2, 1f), is(true));
        assertThat(objectUnderTest.eval(1, 2f), is(false));
        assertThat(objectUnderTest.eval(1, 1f), is(true));
        assertThat(objectUnderTest.eval(2f, 1f), is(true));
        assertThat(objectUnderTest.eval(1f, 2f), is(false));
        assertThat(objectUnderTest.eval(1f, 1f), is(true));
    }

    @Test
    void testEvalInValidArgLength() {
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.eval(1));
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.eval(1, 2, 3));
    }

    @Test
    void testEvalInValidArgType() {
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.eval(1L, 1));
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.eval(1.0, 1));
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.eval(1, 1L));
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.eval(1, 1.0));
    }
}