/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugin;

import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.plugin.NoPluginFoundException;
import com.amazon.dataprepper.model.plugin.PluginFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The primary implementation of {@link PluginFactory}.
 *
 * @since 1.2
 */
@Named
@DependsOn("PluginFactoryConfiguration")
public class DefaultPluginFactory implements PluginFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPluginFactory.class);

    private final Collection<PluginProvider> pluginProviders;
    private final PluginCreator pluginCreator;
    private final PluginConfigurationConverter pluginConfigurationConverter;
    private final ApplicationContext applicationContext;

    @Inject
    DefaultPluginFactory(
            final PluginProviderLoader pluginProviderLoader,
            final PluginCreator pluginCreator,
            final PluginConfigurationConverter pluginConfigurationConverter,
//            final ApplicationContext applicationContext
            @Qualifier(PluginFactoryConfiguration.PLUGIN_APPLICATION_CONTEXT_NAME) final ApplicationContext pluginApplicationContext
    ) {
        Objects.requireNonNull(pluginProviderLoader);
        this.pluginCreator = Objects.requireNonNull(pluginCreator);
        this.pluginConfigurationConverter = Objects.requireNonNull(pluginConfigurationConverter);

        this.pluginProviders = Objects.requireNonNull(pluginProviderLoader.getPluginProviders());
        this.applicationContext = Objects.requireNonNull(pluginApplicationContext);
        LOG.error("Plugin context? {}", this.applicationContext);

        if(pluginProviders.isEmpty()) {
            throw new RuntimeException("Data Prepper requires at least one PluginProvider. " +
                    "Your Data Prepper configuration may be missing the com.amazon.dataprepper.plugin.PluginProvider file.");
        }

        // TODO: Remove this along with the removal of com.amazon.dataprepper.plugins.PluginFactory
        com.amazon.dataprepper.plugins.PluginFactory.dangerousMethod_setPluginFunction(
                ((pluginSetting, aClass) -> pluginCreator.newPluginInstance(aClass, getConstructionContext(pluginSetting, aClass), pluginSetting.getName()))
        );
    }

    @Override
    public <T> T loadPlugin(final Class<T> baseClass, final PluginSetting pluginSetting) {
        final String pluginName = pluginSetting.getName();
        final Class<? extends T> pluginClass = getPluginClass(baseClass, pluginName);

        final PluginArgumentsContext constructionContext = getConstructionContext(pluginSetting, pluginClass);

        return pluginCreator.newPluginInstance(pluginClass, constructionContext, pluginName);
    }

    @Override
    public <T> List<T> loadPlugins(
            final Class<T> baseClass, final PluginSetting pluginSetting,
            final Function<Class<? extends T>, Integer> numberOfInstancesFunction) {

        final String pluginName = pluginSetting.getName();
        final Class<? extends T> pluginClass = getPluginClass(baseClass, pluginName);

        final Integer numberOfInstances = numberOfInstancesFunction.apply(pluginClass);

        if(numberOfInstances == null || numberOfInstances < 0)
            throw new IllegalArgumentException("The numberOfInstances must be provided as a non-negative integer.");

        final PluginArgumentsContext constructionContext = getConstructionContext(pluginSetting, pluginClass);

        final List<T> plugins = new ArrayList<>(numberOfInstances);
        for (int i = 0; i < numberOfInstances; i++) {
            plugins.add(pluginCreator.newPluginInstance(pluginClass, constructionContext, pluginName));
        }
        return plugins;
    }

    private <T> PluginArgumentsContext getConstructionContext(final PluginSetting pluginSetting, final Class<? extends T> pluginClass) {
        final DataPrepperPlugin pluginAnnotation = pluginClass.getAnnotation(DataPrepperPlugin.class);

        final Class<?> pluginConfigurationType = pluginAnnotation.pluginConfigurationType();
        final Object configuration = pluginConfigurationConverter.convert(pluginConfigurationType, pluginSetting);

        return new PluginArgumentsContext.Builder()
                .withPluginSetting(pluginSetting)
                .withPluginConfiguration(configuration)
                .withPluginFactory(this)
                .withPublicApplicationContext(applicationContext)
                .build();
    }

    private <T> Class<? extends T> getPluginClass(final Class<T> baseClass, final String pluginName) {
        return pluginProviders.stream()
                .map(pluginProvider -> pluginProvider.findPluginClass(baseClass, pluginName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElseThrow(() -> new NoPluginFoundException(
                        "Unable to find a plugin named '" + pluginName + "'. Please ensure that plugin is annotated with appropriate values."));
    }
}
