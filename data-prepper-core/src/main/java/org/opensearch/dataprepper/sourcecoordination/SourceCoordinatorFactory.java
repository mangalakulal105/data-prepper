/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.sourcecoordination;

import org.opensearch.dataprepper.model.plugin.PluginFactory;
import org.opensearch.dataprepper.model.source.SourceCoordinator;
import org.opensearch.dataprepper.parser.model.SourceCoordinationConfig;

/**
 * A factory class that will create the {@link org.opensearch.dataprepper.model.source.SourceCoordinator} implementation based on the
 * source_coordination configuration
 * @since 2.2
 */
public class SourceCoordinatorFactory {
    private final SourceCoordinationConfig sourceCoordinationConfig;
    private final PluginFactory pluginFactory;

    public SourceCoordinatorFactory(final SourceCoordinationConfig sourceCoordinationConfig,
                                    final PluginFactory pluginFactory){

        this.sourceCoordinationConfig = sourceCoordinationConfig;
        this.pluginFactory = pluginFactory;
    }

    public <T> SourceCoordinator<T> provideSourceCoordinator(final Class<T> clazz) {
        if (sourceCoordinationConfig == null
                || sourceCoordinationConfig.getSourceCoordinationStoreConfig() == null
                || sourceCoordinationConfig.getSourceCoordinationStoreConfig().getName() == null) {
            return null;
        }

        return pluginFactory.loadPlugin(SourceCoordinator.class, sourceCoordinationConfig.getSourceCoordinationStoreConfig());
    }
}
