/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package com.amazon.dataprepper.plugins.prepper;

import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.annotations.DataPrepperPluginConstructor;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.event.Event;
import com.amazon.dataprepper.model.event.JacksonEvent;
import com.amazon.dataprepper.model.prepper.Prepper;
import com.amazon.dataprepper.model.record.Record;

import java.util.ArrayList;
import java.util.Collection;

/**
 * An simple String implementation of {@link Prepper} which generates new Records with upper case or lowercase content. The current
 * simpler implementation does not handle errors (if any).
 */
@DataPrepperPlugin(name = "string_converter", pluginType = Prepper.class, pluginConfigurationType = StringPrepper.Configuration.class)
public class StringPrepper implements Prepper<Record<Event>, Record<Event>> {

    public static final String UPPER_CASE = "upper_case";

    private final boolean upperCase;

    public static class Configuration {
        private boolean upperCase = true;

        public boolean getUpperCase() {
            return upperCase;
        }

        public void setUpperCase(final boolean upperCase) {
            this.upperCase = upperCase;
        }
    }

    /**
     * Mandatory constructor for Data Prepper Component - This constructor is used by Data Prepper
     * runtime engine to construct an instance of {@link StringPrepper} using an instance of {@link PluginSetting} which
     * has access to pluginSetting metadata from pipeline
     * pluginSetting file.
     *
     * @param configuration instance with metadata information from pipeline pluginSetting file.
     */
    @DataPrepperPluginConstructor
    public StringPrepper(final Configuration configuration) {
        this.upperCase = configuration.getUpperCase();
    }

    @Override
    public Collection<Record<Event>> execute(final Collection<Record<Event>> records) {
        final Collection<Record<Event>> modifiedRecords = new ArrayList<>(records.size());
        for (Record<Event> record : records) {
            final Event recordEvent = record.getData();
            final String recordData = recordEvent.toJsonString();
            final String newData = upperCase? recordData.toUpperCase() : recordData.toLowerCase();
            final Event newRecordEvent = JacksonEvent.builder()
                    .withEventMetadata(recordEvent.getMetadata())
                    .withData(newData)
                    .build();
            modifiedRecords.add(new Record<>(newRecordEvent));
        }
        return modifiedRecords;
    }

    @Override
    public void prepareForShutdown() {

    }

    @Override
    public boolean isReadyForShutdown() {
        return true;
    }


    @Override
    public void shutdown() {

    }
}
