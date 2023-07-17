/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.opensearch.dataprepper.model.event.Event;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.Random;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GenericExpressionEvaluatorTest {

    @Mock
    private Parser<ParseTree> parser;
    @Mock
    private Evaluator<ParseTree, Event> evaluator;
    @InjectMocks
    private GenericExpressionEvaluator statementEvaluator;

    @Test
    void testGivenValidParametersThenEvaluatorResultReturned() {
        final String statement = UUID.randomUUID().toString();
        final ParseTree parseTree = mock(ParseTree.class);
        final Event event = mock(Event.class);
        final String expectedStr = UUID.randomUUID().toString();

        doReturn(parseTree).when(parser).parse(eq(statement));
        doReturn(expectedStr).when(evaluator).evaluate(eq(parseTree), eq(event));

        final Object actualStr = statementEvaluator.evaluate(statement, event);

        assertThat((String)actualStr, is(expectedStr));
        verify(parser).parse(eq(statement));
        verify(evaluator).evaluate(eq(parseTree), eq(event));

        final Random random = new Random();
        final Integer expectedInt = random.nextInt(1000);

        doReturn(parseTree).when(parser).parse(eq(statement));
        doReturn(expectedInt).when(evaluator).evaluate(eq(parseTree), eq(event));

        final Object actualInt = statementEvaluator.evaluate(statement, event);

        assertThat((Integer)actualInt, is(expectedInt));
        verify(parser, times(2)).parse(eq(statement));
        verify(evaluator, times(2)).evaluate(eq(parseTree), eq(event));
    }

    @Test
    void testGivenParserThrowsExceptionThenExceptionThrown() {
        final String statement = UUID.randomUUID().toString();

        doThrow(new RuntimeException()).when(parser).parse(eq(statement));

        assertThrows(ExpressionEvaluationException.class, () -> statementEvaluator.evaluate(statement, null));

        verify(parser).parse(eq(statement));
        verify(evaluator, times(0)).evaluate(any(), any());
    }

    @Test
    void testGivenEvaluatorThrowsExceptionThenExceptionThrown() {
        final String statement = UUID.randomUUID().toString();
        final ParseTree parseTree = mock(ParseTree.class);
        final Event event = mock(Event.class);

        doReturn(parseTree).when(parser).parse(eq(statement));
        doThrow(new RuntimeException()).when(evaluator).evaluate(eq(parseTree), eq(event));

        assertThrows(ExpressionEvaluationException.class, () -> statementEvaluator.evaluateConditional(statement, event));

        verify(parser).parse(eq(statement));
        verify(evaluator).evaluate(eq(parseTree), eq(event));
    }

    @Test
    void isValidExpressionStatement_returns_true_when_parse_does_not_throw() {
        final String statement = UUID.randomUUID().toString();
        final ParseTree parseTree = mock(ParseTree.class);

        doReturn(parseTree).when(parser).parse(eq(statement));

        final boolean result = statementEvaluator.isValidExpressionStatement(statement);

        assertThat(result, equalTo(true));

        verify(parser).parse(eq(statement));
    }

    @Test
    void isValidExpressionStatement_returns_false_when_parse_throws() {
        final String statement = UUID.randomUUID().toString();

        doThrow(RuntimeException.class).when(parser).parse(eq(statement));

        final boolean result = statementEvaluator.isValidExpressionStatement(statement);

        assertThat(result, equalTo(false));
    }

}

