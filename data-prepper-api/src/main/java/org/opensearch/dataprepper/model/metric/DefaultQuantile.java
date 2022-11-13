/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.model.metric;

/**
 * The default implementation of {@link Quantile}, measurement within a {@link Summary}
 *
 * @since 1.4
 */
public class DefaultQuantile implements Quantile {
    private Double quantile;
    private Double value;

    // required for serialization
    DefaultQuantile() {}

    public DefaultQuantile(Double quantile, Double value) {
        this.quantile = quantile;
        this.value = value;
    }

    @Override
    public Double getValue() {
        return value;
    }

    @Override
    public Double getQuantile() {
        return quantile;
    }
}

