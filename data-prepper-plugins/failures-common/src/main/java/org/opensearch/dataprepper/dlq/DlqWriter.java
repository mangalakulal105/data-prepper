/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package main.java.org.opensearch.dataprepper.dlq;


import org.opensearch.dataprepper.model.failures.DlqObject;

import java.io.IOException;
import java.util.List;

/**
 * An interface for writing DLQ objects to the DLQ
 *
 * @since 2.2
 */
public interface DlqWriter {

    /**
     * Writes the DLQ objects to the DLQ
     * @param dlqObjects the list of objects to be written to the DLQ
     * @throws IOException
     *
     * @since 2.2
     */
    void write(final List<DlqObject> dlqObjects) throws IOException;

    /**
     * Closes any open connections to the DLQ
     * @throws IOException
     *
     * @since 2.2
     */
    void close() throws IOException;
}
