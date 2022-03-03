/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugin;

import com.amazon.dataprepper.model.annotations.DataPrepperPlugin;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.plugin.InvalidPluginConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import javax.inject.Named;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts and validates a plugin configuration. This class is responsible for taking a {@link PluginSetting}
 * and converting it to the plugin model type which should be denoted by {@link DataPrepperPlugin#pluginConfigurationType()}
 */
@Named
class PluginConfigurationConverter {
    private final ObjectMapper objectMapper;
    private final Validator validator;

    PluginConfigurationConverter(final Validator validator) {
        final SimpleModule simpleModule = new SimpleModule();
        simpleModule.addDeserializer(Duration.class, new PluginDurationDeserializer());

        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(simpleModule);

        this.validator = validator;
    }

    /**
     * Converts and validates to a plugin model type. The conversion happens via
     * Java Bean Validation 2.0.
     *
     * @param pluginConfigurationType the destination type
     * @param pluginSetting The source {@link PluginSetting}
     * @return The converted object of type pluginConfigurationType
     * @throws InvalidPluginConfigurationException - If the plugin configuration is invalid
     */
    public Object convert(final Class<?> pluginConfigurationType, final PluginSetting pluginSetting) {
        Objects.requireNonNull(pluginConfigurationType);
        Objects.requireNonNull(pluginSetting);

        if(pluginConfigurationType.equals(PluginSetting.class))
            return pluginSetting;

        final Object configuration = objectMapper.convertValue(pluginSetting.getSettings(), pluginConfigurationType);

        final Set<ConstraintViolation<Object>> constraintViolations = validator.validate(configuration);

        if(!constraintViolations.isEmpty()) {
            final String violationsString = constraintViolations.stream()
                    .map(v -> v.getPropertyPath().toString() + " " + v.getMessage())
                    .collect(Collectors.joining(". "));

            final String exceptionMessage = String.format("Plugin %s in pipeline %s is configured incorrectly: %s",
                    pluginSetting.getName(), pluginSetting.getPipelineName(), violationsString);
            throw new InvalidPluginConfigurationException(exceptionMessage);
        }

        return configuration;
    }
}
