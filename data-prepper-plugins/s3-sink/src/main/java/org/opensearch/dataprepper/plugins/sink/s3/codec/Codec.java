/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.s3.codec;

import java.io.IOException;
import org.opensearch.dataprepper.model.event.Event;

/**
 * Each implementation of this class should support parsing a specific type or format of data. See
 * sub-classes for examples.
 */
public interface Codec {
    /**
     * @param event input data.
     * @param tagsTargetKey key name for including tags if not null
     * @return parse string.
     * @throws IOException exception.
     */
    String parse(final Event event, final String tagsTargetKey) throws IOException;
}
