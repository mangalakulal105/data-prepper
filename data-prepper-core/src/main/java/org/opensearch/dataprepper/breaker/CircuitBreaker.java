/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.breaker;

/**
 * Represents a circuit breaker in Data Prepper.
 *
 * @since 2.1
 */
public interface CircuitBreaker {
    /**
     * Checks a circuit breaker. If open, then the circuit breaker has
     * been tripped.
     *
     * @return true if open; false if closed.
     * @since 2.1
     */
    boolean isOpen();
}
