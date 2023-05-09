/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;

class LengthExpressionFunctionTest {
    private LengthExpressionFunction lengthExpressionFunction;

    public LengthExpressionFunction createObjectUnderTest() {
        return new LengthExpressionFunction();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 5, 10, 20, 50})
    void testWithOneStringArgumentWithOutQuotes(int stringLength) {
        lengthExpressionFunction = createObjectUnderTest();
        String testString = RandomStringUtils.randomAlphabetic(stringLength);
        assertThat(lengthExpressionFunction.evaluate(List.of(testString)), equalTo(testString.length()));
    }

    @Test
    void testWithOneStringArgumentWithQuotes() {
        lengthExpressionFunction = createObjectUnderTest();
        String testString = RandomStringUtils.randomAlphabetic(5);
        assertThat(lengthExpressionFunction.evaluate(List.of("\""+testString + "\"")), equalTo(testString.length()));
    }

    @Test
    void testWithTwoArgs() {
        lengthExpressionFunction = createObjectUnderTest();
        assertThrows(RuntimeException.class, () -> lengthExpressionFunction.evaluate(List.of("arg1", "arg2")));
    }
    
    @Test
    void testWithNonStringArgument() {
        lengthExpressionFunction = createObjectUnderTest();
        assertThrows(RuntimeException.class, () -> lengthExpressionFunction.evaluate(List.of(10)));
    }
    
    @Test
    void testWithInvalidStringArgument() {
        lengthExpressionFunction = createObjectUnderTest();
        assertThrows(RuntimeException.class, () -> lengthExpressionFunction.evaluate(List.of("\"arg1")));
    }
}
