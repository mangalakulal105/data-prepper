/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.antlr.v4.runtime.ParserRuleContext;

interface Operator<T> {
    boolean shouldEvaluate(final ParserRuleContext ctx);

    int getSymbol();

    /**
     * @since 1.3
     * Placeholder interface for implementing Data-Prepper supported binary/unary operations on operands that
     * returns custom type T.
     * @param args operands
     * @return T
     */
    T evaluate(final Object... args);
}
