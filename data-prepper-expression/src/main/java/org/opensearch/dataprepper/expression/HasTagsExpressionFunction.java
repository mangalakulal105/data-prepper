/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.opensearch.dataprepper.model.event.Event;
import java.util.List;
import java.util.Set;
import javax.inject.Named;
import java.util.function.Function;

@Named
public class HasTagsExpressionFunction implements ExpressionFunction {
    public String getFunctionName() {
        return "hasTags";
    }

    public Object evaluate(final List<Object> args, Event event, Function<Object, Object> convertLiteralType) {
        if (args.size() == 0) {
            throw new RuntimeException("hasTags() takes at least one argument");
        }
        Set<String> tags = event.getMetadata().getTags();
        for (final Object arg: args) {
            if (!(arg instanceof String)) {
                throw new RuntimeException("hasTags() takes only String type arguments");
            }
            if (!tags.contains((String)arg)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }
}


