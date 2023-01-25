/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.file;

import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.buffer.Buffer;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.plugin.PluginFactory;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.source.Source;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.opensearch.dataprepper.logging.DataPrepperMarkers.SENSITIVE;

@DataPrepperPlugin(name = "file", pluginType = Source.class, pluginConfigurationType = FileSourceConfig.class)
public class FileSource implements Source<Record<Object>> {

    static final String MESSAGE_KEY = "message";
    private static final Logger LOG = LoggerFactory.getLogger(FileSource.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {};

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final FileSourceConfig fileSourceConfig;

    private boolean isStopRequested;
    private final int writeTimeout;

    @DataPrepperPluginConstructor
    public FileSource(final FileSourceConfig fileSourceConfig, final PluginMetrics pluginMetrics, final PluginFactory pluginFactory) {
        fileSourceConfig.validate();
        this.fileSourceConfig = fileSourceConfig;
        this.isStopRequested = false;
        this.writeTimeout = FileSourceConfig.DEFAULT_TIMEOUT;
    }


    @Override
    public void start(final Buffer<Record<Object>> buffer) {
        checkNotNull(buffer, "Buffer cannot be null for file source to start");
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileSourceConfig.getFilePathToRead()), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && !isStopRequested) {
                writeLineAsEventOrString(line, buffer);
            }
        } catch (IOException | TimeoutException | IllegalArgumentException ex) {
            LOG.error("Error processing the input file path [{}]", fileSourceConfig.getFilePathToRead(), ex);
            throw new RuntimeException(format("Error processing the input file %s",
                    fileSourceConfig.getFilePathToRead()), ex);
        }
    }

    @Override
    public void stop() {
        isStopRequested = true;
    }

    private Record<Object> getEventRecordFromLine(final String line) {
        Map<String, Object> structuredLine = new HashMap<>();

        switch(fileSourceConfig.getFormat()) {
            case JSON:
                structuredLine = parseJson(line);
                break;
            case PLAIN:
                structuredLine.put(MESSAGE_KEY, line);
                break;
        }

        return new Record<>(JacksonEvent
                .builder()
                .withEventType(fileSourceConfig.getRecordType())
                .withData(structuredLine)
                .build());
    }

    private Map<String, Object> parseJson(final String jsonString) {
        try {
            return OBJECT_MAPPER.readValue(jsonString, MAP_TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            LOG.error(SENSITIVE, "Unable to parse json data [{}], assuming plain text", jsonString, e);
            final Map<String, Object> plainMap = new HashMap<>();
            plainMap.put(MESSAGE_KEY, jsonString);
            return plainMap;
        }
    }

    // Temporary function to support both trace and log ingestion pipelines.
    // TODO: This function should be removed with the completion of: https://github.com/opensearch-project/data-prepper/issues/546
    private void writeLineAsEventOrString(final String line, final Buffer<Record<Object>> buffer) throws TimeoutException, IllegalArgumentException {
        if (fileSourceConfig.getRecordType().equals(FileSourceConfig.EVENT_TYPE)) {
            buffer.write(getEventRecordFromLine(line), writeTimeout);
        } else if (fileSourceConfig.getRecordType().equals(FileSourceConfig.DEFAULT_TYPE)) {
            buffer.write(new Record<>(line), writeTimeout);
        }
    }
}