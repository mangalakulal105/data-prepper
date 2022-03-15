/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.logstash.mapping.mutate;

import java.util.List;

public class TrimStringConversion extends AbstractConversion<String> {
    public static String getLogstashName() {
        return "trim";
    }

    @Override
    protected void addKvToEntries(final String key, final Object value) {}

    @Override
    protected void addListToEntries(final List<String> list) {
        entries.addAll(list);
    }

    @Override
    protected String getDataPrepperName() {
        return "trim_string";
    }

    @Override
    protected String getMapKey() {
        return "with_keys";
    }
}
