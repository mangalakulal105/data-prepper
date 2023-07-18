/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch.index;

import org.opensearch.client.opensearch.OpenSearchClient;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a template type in OpenSearch.
 */
public enum TemplateType {
    /**
     * The v1 template type.
     */
    V1("v1", V1TemplateStrategy::new),

    /**
     * Index template type.
     */
    INDEX_TEMPLATE("index-template", ComposableIndexTemplateStrategy::new);

    private static final Map<String, TemplateType> TYPE_NAME_MAP = Arrays.stream(TemplateType.values())
            .collect(Collectors.toMap(
                    value -> value.name,
                    value -> value
            ));

    private final String name;
    private final Function<OpenSearchClient, TemplateStrategy> factoryFunction;

    TemplateType(final String name, final Function<OpenSearchClient, TemplateStrategy> factoryFunction) {
        this.name = name;
        this.factoryFunction = factoryFunction;
    }

    public static TemplateType fromTypeName(final String name) {
        return TYPE_NAME_MAP.get(name);
    }

    public TemplateStrategy createTemplateStrategy(final OpenSearchClient openSearchClient) {
        return factoryFunction.apply(openSearchClient);
    }

    String getTypeName() {
        return name;
    }
}
