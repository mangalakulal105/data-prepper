/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.expression.antlr.DataPrepperExpressionParser;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegexNotEqualOperatorTest {
    final GenericRegexMatchOperator objectUnderTest = new OperatorFactory().regexNotEqualOperator();

    @Mock
    private ParserRuleContext ctx;

    @Test
<<<<<<< HEAD
    void testGetNumberOfOperands() {
        assertThat(objectUnderTest.getNumberOfOperands(), is(2));
    }

    @Test
=======
>>>>>>> main
    void testShouldEvaluate() {
        when(ctx.getRuleIndex()).thenReturn(DataPrepperExpressionParser.RULE_regexOperatorExpression);
        assertThat(objectUnderTest.shouldEvaluate(ctx), is(true));
        when(ctx.getRuleIndex()).thenReturn(-1);
        assertThat(objectUnderTest.shouldEvaluate(ctx), is(false));
    }

    @Test
    void testGetSymbol() {
        assertThat(objectUnderTest.getSymbol(), is(DataPrepperExpressionParser.NOT_MATCH_REGEX_PATTERN));
    }

    @Test
    void testEvalValidArgs() {
        assertThat(objectUnderTest.evaluate("a", "a*"), is(false));
        assertThat(objectUnderTest.evaluate("a", "b*"), is(true));
    }

    @Test
    void testEvalInValidArgLength() {
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.evaluate("a"));
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.evaluate("a", "a", "a*"));
    }

    @Test
    void testEvalInValidArgType() {
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.evaluate(1, "a*"));
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.evaluate("a", 1));
    }

    @Test
    void testEvalInValidPattern() {
        assertThrows(IllegalArgumentException.class, () -> objectUnderTest.evaluate("a", "*"));
    }
}