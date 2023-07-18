/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.antlr.v4.runtime.tree.ParseTree;
import org.opensearch.dataprepper.model.event.Event;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Public class that {@link org.opensearch.dataprepper.model.processor.Processor},
 * {@link org.opensearch.dataprepper.model.sink.Sink} and data-prepper-core objects can use to evaluate statements.
 */
@Named
class GenericExpressionEvaluator implements ExpressionEvaluator {
    private final Parser<ParseTree> parser;
    private final Evaluator<ParseTree, Event> evaluator;

    @Inject
    public GenericExpressionEvaluator(final Parser<ParseTree> parser, final Evaluator<ParseTree, Event> evaluator) {
        this.parser = parser;
        this.evaluator = evaluator;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ExpressionEvaluationException if unable to evaluate or coerce the statement result to type T
     */
    @Override
    public Object evaluate(final String statement, final Event context) {
        try {
            final ParseTree parseTree = parser.parse(statement);
            return evaluator.evaluate(parseTree, context);
        }
        catch (final Exception exception) {
            throw new ExpressionEvaluationException("Unable to evaluate statement \"" + statement + "\"", exception);
        }
    }

    @Override
    public Boolean isValidExpressionStatement(final String statement) {
        try {
            parser.parse(statement);
            return true;
        }
        catch (final Exception exception) {
            return false;
        }
    }
}
