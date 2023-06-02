/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.opensearch.worker;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.cat.IndicesResponse;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.dataprepper.model.source.coordinator.PartitionIdentifier;
import org.opensearch.dataprepper.plugins.source.opensearch.OpenSearchSourceConfiguration;
import org.opensearch.dataprepper.plugins.source.opensearch.configuration.IndexParametersConfiguration;
import org.opensearch.dataprepper.plugins.source.opensearch.configuration.OpenSearchIndex;
import org.opensearch.dataprepper.plugins.source.opensearch.worker.client.ClusterClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class OpenSearchIndexPartitionCreationSupplier implements Function<Map<String, Object>, List<PartitionIdentifier>> {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchIndexPartitionCreationSupplier.class);

    private final OpenSearchSourceConfiguration openSearchSourceConfiguration;
    private final OpenSearchClient openSearchClient;

    public OpenSearchIndexPartitionCreationSupplier(final OpenSearchSourceConfiguration openSearchSourceConfiguration,
                                                    final ClusterClientFactory clusterClientFactory) {
        this.openSearchSourceConfiguration = openSearchSourceConfiguration;

        final Object client = clusterClientFactory.getClient();

        if (client instanceof OpenSearchClient) {
            this.openSearchClient = (OpenSearchClient) client;
        } else {
            throw new IllegalArgumentException(String.format("ClusterClientFactory provided an invalid client object to the index partition creation supplier. " +
                    "The client must be of type OpenSearchClient. The client passed is of class %s", client.getClass()));
        }

    }

    @Override
    public List<PartitionIdentifier> apply(final Map<String, Object> globalStateMap) {

        if (Objects.nonNull(openSearchClient)) {
            return applyForOpenSearchClient(globalStateMap);
        }

        return Collections.emptyList();
    }

    private List<PartitionIdentifier> applyForOpenSearchClient(final Map<String, Object> globalStateMap) {
        IndicesResponse indicesResponse;
        try {
            indicesResponse = openSearchClient.cat().indices();
        } catch (IOException | OpenSearchException e) {
            LOG.error("There was an exception when calling /_cat/indices to create new index partitions", e);
            return Collections.emptyList();
        }

        return indicesResponse.valueBody().stream()
                .filter(this::isIndexIncludedInOneOfTheIncludePatternsAndNotExcludedInAnExcludePattern)
                .map(indexRecord -> PartitionIdentifier.builder().withPartitionKey(indexRecord.index()).build())
                .collect(Collectors.toList());
    }

    private boolean isIndexIncludedInOneOfTheIncludePatternsAndNotExcludedInAnExcludePattern(final IndicesRecord indicesRecord) {
        if (Objects.isNull(indicesRecord.index())) {
            return false;
        }

        final IndexParametersConfiguration indexParametersConfiguration = openSearchSourceConfiguration.getIndexParametersConfiguration();

        if (Objects.isNull(openSearchSourceConfiguration.getIndexParametersConfiguration())) {
            return true;
        }

        final List<OpenSearchIndex> includedIndices = indexParametersConfiguration.getIncludedIndices();
        final List<OpenSearchIndex> excludedIndices = indexParametersConfiguration.getExcludedIndices();

        boolean matchesIncludedPattern = includedIndices.isEmpty();


        for (final OpenSearchIndex index : includedIndices) {
            final Matcher matcher = index.getIndexNamePattern().matcher(indicesRecord.index());

            if (matcher.matches()) {
                matchesIncludedPattern = true;
                break;
            }
        }

        boolean matchesExcludePattern = false;

        for (final OpenSearchIndex index : excludedIndices) {
            final Matcher matcher = index.getIndexNamePattern().matcher(indicesRecord.index());

            if (matcher.matches()) {
                matchesExcludePattern = true;
                break;
            }
        }


        return matchesIncludedPattern && !matchesExcludePattern;
    }
}
