/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.accumulator;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A buffer can hold data before flushing it any Sink.
 */
public interface Buffer {

    /**
     * Gets the current size of the buffer. This should be the number of bytes.
     * @return buffer size.
     */
    long getSize();
    int getEventCount();
    long getDuration();

    byte[] getSinkBufferData() throws IOException;

    OutputStream getOutputStream();

    void setEventCount(int eventCount);
}
