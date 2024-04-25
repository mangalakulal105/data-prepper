/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.pipeline.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import static java.lang.String.format;
import org.opensearch.dataprepper.model.configuration.DataPrepperVersion;
import org.opensearch.dataprepper.model.configuration.PipelineExtensions;
import org.opensearch.dataprepper.model.configuration.PipelineModel;
import org.opensearch.dataprepper.model.configuration.PipelinesDataFlowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PipelinesDataflowModelParser {
    private static final Logger LOG = LoggerFactory.getLogger(PipelinesDataflowModelParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

    private final PipelineConfigurationReader pipelineConfigurationReader;

    public PipelinesDataflowModelParser(final PipelineConfigurationReader pipelineConfigurationReader) {
        this.pipelineConfigurationReader = pipelineConfigurationReader;
    }


    //TODO
    // move transformation code after user pipeline validation in pipelineTransformer.java
    public PipelinesDataFlowModel parseConfiguration() {
        final List<PipelinesDataFlowModel> pipelinesDataFlowModels = parseStreamsToPipelinesDataFlowModel();
        PipelinesDataFlowModel pipelinesDataFlowModel = mergePipelinesDataModels(pipelinesDataFlowModels);

//        performPipelineConfigurationTransformationIfNeeded(pipelinesDataFlowModel);

        return pipelinesDataFlowModel;
    }

    private void validateDataPrepperVersion(final DataPrepperVersion version) {
        if (Objects.nonNull(version) && !DataPrepperVersion.getCurrentVersion().compatibleWith(version)) {
            LOG.error("The version: {} is not compatible with the current version: {}", version, DataPrepperVersion.getCurrentVersion());
            throw new ParseException(format("The version: %s is not compatible with the current version: %s",
                    version, DataPrepperVersion.getCurrentVersion()));
        }
    }

    private List<PipelinesDataFlowModel> parseStreamsToPipelinesDataFlowModel() {
        return pipelineConfigurationReader.getPipelineConfigurationInputStreams().stream()
                .map(this::parseStreamToPipelineDataFlowModel)
                .collect(Collectors.toList());
    }

    //TODO
//    private PipelinesDataFlowModel performPipelineConfigurationTransformationIfNeeded(PipelinesDataFlowModel pipelinesDataFlowModel) {
//
//        //check if transformation is required based on rules present in yaml file
//        RuleEvaluator ruleEvaluator = new RuleEvaluator(pipelinesDataFlowModel);
//        PipelineConfigurationTransformer configurationTransformer = new DynamicYamlTransformer();
//
//        if (ruleEvaluator.isTransformationNeeded()) {
//            PipelineTemplateModel templateModel = ruleEvaluator.getTemplateModel();
//            PipelinesDataFlowModel transformedPipelineDataModel = configurationTransformer.transformConfiguration(pipelinesDataFlowModel, templateModel);
//            return transformedPipelineDataModel;
//        }
//        return pipelinesDataFlowModel;
//    }

    private PipelinesDataFlowModel parseStreamToPipelineDataFlowModel(final InputStream configurationInputStream) {
        try (final InputStream pipelineConfigurationInputStream = configurationInputStream) {
            final PipelinesDataFlowModel pipelinesDataFlowModel = OBJECT_MAPPER.readValue(pipelineConfigurationInputStream,
                    PipelinesDataFlowModel.class);

            final DataPrepperVersion version = pipelinesDataFlowModel.getDataPrepperVersion();
            validateDataPrepperVersion(version);

            return pipelinesDataFlowModel;
        } catch (IOException e) {
            throw new ParseException("Failed to parse the configuration", e);
        }
    }

    private PipelinesDataFlowModel parseStreamToTemplateDataFlowModel(final InputStream configurationInputStream) {
        try (final InputStream pipelineConfigurationInputStream = configurationInputStream) {
            final PipelinesDataFlowModel pipelinesDataFlowModel = OBJECT_MAPPER.readValue(pipelineConfigurationInputStream,
                    PipelinesDataFlowModel.class);

            return pipelinesDataFlowModel;
        } catch (IOException e) {
            throw new ParseException("Failed to parse the configuration", e);
        }
    }

    private PipelinesDataFlowModel mergePipelinesDataModels(
            final List<PipelinesDataFlowModel> pipelinesDataFlowModels) {
        final Map<String, PipelineModel> pipelinesDataFlowModelMap = pipelinesDataFlowModels.stream()
                .map(PipelinesDataFlowModel::getPipelines)
                .flatMap(pipelines -> pipelines.entrySet().stream())
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
        final List<PipelineExtensions> pipelineExtensionsList = pipelinesDataFlowModels.stream()
                .map(PipelinesDataFlowModel::getPipelineExtensions)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (pipelineExtensionsList.size() > 1 ||
                (pipelineExtensionsList.size() == 1 && pipelinesDataFlowModels.size() > 1)) {
            throw new ParseException(
                    "extension/pipeline_configurations and definition must all be defined in a single YAML file if extension/pipeline_configurations is configured.");
        }
        return pipelineExtensionsList.isEmpty() ? new PipelinesDataFlowModel(pipelinesDataFlowModelMap) :
                new PipelinesDataFlowModel(pipelineExtensionsList.get(0), pipelinesDataFlowModelMap);
    }

}
