/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.sink.opensearch;

import org.opensearch.common.unit.TimeValue;

import java.util.Iterator;

public final class BackOffUtils {
    private final Iterator<TimeValue> iterator;

    private long currTime = 0;

    private boolean firstAttempt = true;

    public BackOffUtils(final Iterator<TimeValue> iterator) {
        this.iterator = iterator;
    }

    public boolean hasNext() {
        return firstAttempt || iterator.hasNext();
    }

    public boolean next() throws InterruptedException {
        if (firstAttempt) {
            firstAttempt = false;
            return true;
        }
        if (!iterator.hasNext()) {
            return false;
        } else {
            final long nextTime = iterator.next().getMillis();
            Thread.sleep(nextTime - currTime);
            currTime = nextTime;
            return true;
        }
    }
}
