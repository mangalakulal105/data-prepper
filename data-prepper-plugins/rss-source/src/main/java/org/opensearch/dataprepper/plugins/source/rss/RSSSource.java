/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.rss;

import com.apptasticsoftware.rssreader.Item;
import com.apptasticsoftware.rssreader.RssReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.buffer.Buffer;
import org.opensearch.dataprepper.model.document.Document;
import org.opensearch.dataprepper.model.document.JacksonDocument;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.source.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DataPrepperPlugin(name = "rss", pluginType = Source.class, pluginConfigurationType =  RSSSourceConfig.class)
public class RSSSource implements Source<Record<Document>> {

    private static final Logger LOG = LoggerFactory.getLogger(RSSSource.class);

    private final PluginMetrics pluginMetrics;

    private final RSSSourceConfig rssSourceConfig;

    private final ScheduledExecutorService scheduledExecutorService;


    @DataPrepperPluginConstructor
    public RSSSource(final PluginMetrics pluginMetrics, final RSSSourceConfig rssSourceConfig) {
        this.pluginMetrics = pluginMetrics;
        this.rssSourceConfig = rssSourceConfig;
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void start(final Buffer<Record<Document>> buffer) {
        if (buffer == null) {
            throw new IllegalStateException("Buffer is null");
        }
        Runnable task1 = () -> {
            final RssReader reader = new RssReader();
            Stream<Item> itemStream;
            try {
                LOG.info("Reading RSS Feed URL");
                itemStream = reader.read(rssSourceConfig.getUrl());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            LOG.info("RSS feed URL read successfully. Proceeding to collect Items from URL");
            List<Item> items = itemStream.collect(Collectors.toList());
            for (Item item: items) {
                LOG.info("Converting Feed Item to an Event Document");
                Record<Document> document = buildEventDocument(item);
                try {
                    buffer.write(document, 500);
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        scheduledExecutorService.scheduleAtFixedRate(task1, 0, 5, TimeUnit.MINUTES);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        Thread.interrupted();
        scheduledExecutorService.shutdownNow();
    }

    private Record<Document> buildEventDocument(final Item item) {
        final JacksonDocument document = JacksonDocument.builder()
                .withData(item)
                .getThis()
                .build();
        return new Record<>(document);
    }
}
