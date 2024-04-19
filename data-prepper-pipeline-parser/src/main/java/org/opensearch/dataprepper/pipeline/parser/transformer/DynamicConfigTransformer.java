/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.pipeline.parser.transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import static java.lang.String.format;
import org.opensearch.dataprepper.model.configuration.PipelineModel;
import org.opensearch.dataprepper.model.configuration.PipelinesDataFlowModel;
import org.opensearch.dataprepper.pipeline.parser.rule.RuleEvaluator;
import org.opensearch.dataprepper.pipeline.parser.rule.RuleEvaluatorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicConfigTransformer implements PipelineConfigurationTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicConfigTransformer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleEvaluator ruleEvaluator;
    private final PipelinesDataFlowModel preTransformedPipelinesDataFlowModel;

    // Placeholder will look like "<<placeholderValue>>"
    Pattern placeholderPattern = Pattern.compile("\\<\\<\\s*(.+?)\\s*>>");
    String pipelineNamePlaceholderRegex = "\\<\\<\\s*" + Pattern.quote("pipeline-name") + "\\s*\\>\\>";

    //This is the root node of the template json. This is got when converting template model to
    // corresponding json and will always be a constant
    String templatePipelineRootString = "templatePipelines";

    // Json Path expression like "?(@.<node>)" seem to always return arrayNode even if it is an ObjectNode.
    // jsonPathArrayDisambiguatorPattern is a way used to detect and disambiguate the path.
    String jsonPathArrayDisambiguatorPattern = "[?(@.";

    Configuration parseConfigWithJsonNode = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    public DynamicConfigTransformer(PipelinesDataFlowModel preTransformedDataFlowModel,
                                    RuleEvaluator ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
        this.preTransformedPipelinesDataFlowModel = preTransformedDataFlowModel;
    }

    /**
     * High Level Explanation:
     * Step1: Evaluate if transformation is needed
     * Step2: Create a Map(placeholdersMap) with key as placeholder and value as List of JsonPath
     * in templateJson. It is populated by recursively by tracking the placeholder and along the way,
     * store the paths.
     * Step3: Create a Map(pipelineExactPathMap) with exact path for the placeholder value in the
     * original pipelineJson.
     * Step4: For every placeholder, replace the template in the corresponding template json path with
     * node from the original pipelineJson.
     * Step5: Convert result to PipelinesDataFlowModel.
     * @return PipelinesDataFlowModel - Transformed PipelinesDataFlowModel.
     */
    @Override
    public PipelinesDataFlowModel transformConfiguration() {
        RuleEvaluatorResult ruleEvaluatorResult = ruleEvaluator.isTransformationNeeded(preTransformedPipelinesDataFlowModel);

        if (!ruleEvaluatorResult.isEvaluatedResult() ||
                ruleEvaluatorResult.getPipelineName() == null) {
            return preTransformedPipelinesDataFlowModel;
        }

        //To differentiate between sub-pipelines that dont need transformation.
        String pipelineNameThatNeedsTransformation = ruleEvaluatorResult.getPipelineName();
        PipelineTemplateModel templateModel = ruleEvaluatorResult.getPipelineTemplateModel();
        try {

            Map<String, PipelineModel> pipelines = preTransformedPipelinesDataFlowModel.getPipelines();
            Map<String, PipelineModel> pipelineMap = new HashMap<>();
            pipelineMap.put(pipelineNameThatNeedsTransformation,
                    pipelines.get(pipelineNameThatNeedsTransformation));
            String pipelineJson = objectMapper.writeValueAsString(pipelineMap);

            String templateJsonStringWithPipelinePlaceholder = objectMapper.writeValueAsString(templateModel);
            String templateJsonString = replaceTemplatePipelineName(templateJsonStringWithPipelinePlaceholder,
                    pipelineNameThatNeedsTransformation);

            //find all placeholderPattern in template json string
            Map<String, List<String>> placeholdersMap = findPlaceholdersWithPathsRecursively(templateJsonString);
            JsonNode templateRootNode = objectMapper.readTree(templateJsonString);

            // get exact path in pipelineJson
            Map<String, String> pipelineExactPathMap = findExactPath(placeholdersMap, pipelineNameThatNeedsTransformation);

            //replace placeholder with actual value in the template context
            placeholdersMap.forEach((placeholder, templateJsonPathList) -> {
                for (String templateJsonPath : templateJsonPathList) {
                    String pipelineExactJsonPath = pipelineExactPathMap.get(placeholder);
                    JsonNode pipelineNode = JsonPath.using(parseConfigWithJsonNode).parse(pipelineJson).read(pipelineExactJsonPath);

                    // Json Path expression like "?(@.<node>)" seem to always return arrayNode even if it is an Object.
                    // example: $.pipeline.sink[?(@.opensearch)].opensearch.aws expression will always return array
                    if(pipelineExactJsonPath.contains(jsonPathArrayDisambiguatorPattern) &&
                            pipelineNode.isArray() && pipelineNode.size()==1){
                        pipelineNode = pipelineNode.get(0);
                    }
                    replaceNode(templateRootNode, templateJsonPath, pipelineNode);
                }
            });

            PipelinesDataFlowModel transformedPipelinesDataFlowModel = getTransformedPipelinesDataFlowModel(pipelineNameThatNeedsTransformation, pipelines, templateRootNode);
            return transformedPipelinesDataFlowModel;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert templateRootNode which contains the transformedJson to PipelinesDataFlowModel
     *
     * @param pipelineNameThatNeedsTransformation
     * @param pipelines
     * @param templateRootNode - transformedJson Node.
     * @return PipelinesDataFlowModel - transformed model.
     * @throws JsonProcessingException
     */
    private PipelinesDataFlowModel getTransformedPipelinesDataFlowModel(String pipelineNameThatNeedsTransformation, Map<String, PipelineModel> pipelines, JsonNode templateRootNode) throws JsonProcessingException {
        //update template json
        String transformedJson = objectMapper.writeValueAsString(templateRootNode);

        //convert TransformedJson to PipelineModel with the data from preTransformedDataFlowModel.
        //transform transformedJson to Map
        Map<String, Object> transformedConfigMap = objectMapper.readValue(transformedJson, Map.class);


        // get the root of the Transformed Pipeline Model, to get the actual pipelines.
        // direct conversion to PipelineDataModel throws exception.
        Map<String, PipelineModel> transformedPipelines = (Map<String, PipelineModel>) transformedConfigMap.get(templatePipelineRootString);
        pipelines.forEach((pipelineName, pipeline) -> {
            if (!pipelineName.equals(pipelineNameThatNeedsTransformation)) {
                transformedPipelines.put(pipelineName, pipeline);
            }
        });

        // version is not required here as it is already handled in parseStreamToPipelineDataFlowModel
        PipelinesDataFlowModel transformedPipelinesDataFlowModel = new PipelinesDataFlowModel(
                preTransformedPipelinesDataFlowModel.getPipelineExtensions(),
                transformedPipelines
        );
        return transformedPipelinesDataFlowModel;
    }

    private String replaceTemplatePipelineName(String templateJsonStringWithPipelinePlaceholder, String pipelineName) {
        return templateJsonStringWithPipelinePlaceholder.replaceAll(pipelineNamePlaceholderRegex, pipelineName);
    }

    /**
     *  Recursively walks through the json to find the placeholder with a certain regEx pattern,
     *  along the way keeps track of the path.
     * @param json
     * @return Map<String, List<String>> , K:placeholder, V: list of jsonPath in templateJson
     * @throws IOException
     */
    private Map<String, List<String>> findPlaceholdersWithPathsRecursively(String json) throws IOException {

        JsonNode rootNode = objectMapper.readTree(json);
        Map<String, List<String>> placeholdersWithPaths = new HashMap<>();
        populateMapWithPlaceholderPaths(rootNode, "", placeholdersWithPaths);
        return placeholdersWithPaths;
    }

    private void populateMapWithPlaceholderPaths(JsonNode currentNode, String currentPath, Map<String, List<String>> placeholdersWithPaths) {
        if (currentNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = currentNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String path = currentPath.isEmpty() ? entry.getKey() : currentPath + "." + entry.getKey();
                populateMapWithPlaceholderPaths(entry.getValue(), path, placeholdersWithPaths);
            }
        } else if (currentNode.isArray()) {
            for (int i = 0; i < currentNode.size(); i++) {
                String path = currentPath + "[" + i + "]";
                populateMapWithPlaceholderPaths(currentNode.get(i), path, placeholdersWithPaths);
            }
        } else if (currentNode.isValueNode()) {
            String placeHolderValue = currentNode.asText();
            Matcher matcher = placeholderPattern.matcher(placeHolderValue);
            if (matcher.find()) {
                if (!placeholdersWithPaths.containsKey(placeHolderValue)) {
                    List<String> paths = new ArrayList<>();
                    paths.add(currentPath);
                    placeholdersWithPaths.put(placeHolderValue, paths);
                } else {
                    List<String> existingPaths = placeholdersWithPaths.get(placeHolderValue);
                    existingPaths.add(currentPath);
                    placeholdersWithPaths.put(placeHolderValue, existingPaths);
                }
            }
        }
    }

    /**
     * Gets exact path - this is to avoid
     * getting array values(even though it might not be an array) given
     * a recursive expression like "$..<>"
     * @param placeholdersMap
     * @param pipelineName
     * @return Map<String,String> K:jsonPath, V:exactPath
     * @throws IOException
     */
    private Map<String, String> findExactPath(Map<String, List<String>> placeholdersMap, String pipelineName) throws IOException {
        Map<String, String> mapWithPaths = new HashMap<>();
        for (String genericPathPlaceholder : placeholdersMap.keySet()) {
            String genericPath = getValueFromPlaceHolder(genericPathPlaceholder);
            if (genericPath.contains("$.*.")) {
                String exactPath = genericPath.replace("$.*.", "$." + pipelineName + ".");
                mapWithPaths.put(genericPathPlaceholder, exactPath);
            }
        }
        return mapWithPaths;
    }

    /**
     *  Get value from the placeholder field.
     *  Removes the brackets surrounding the value.
     * @param placeholder
     * @return String - placeholder value.
     */
    private String getValueFromPlaceHolder(String placeholder) {
        // placeholder should be valid here as it is regEx matched in populateMapWithPlaceholderPaths
        return placeholder.substring(2, placeholder.length() - 2);
    }

    /**
     *  Replaces template node in the jsonPath with the node from
     *  original json.
     * @param root
     * @param jsonPath
     * @param newNode
     */
    public void replaceNode(JsonNode root, String jsonPath, JsonNode newNode) {
        try {
            if(newNode == null){
                throw new PathNotFoundException(format("jsonPath {} not found",jsonPath));
            }
            // Read the parent path of the target node
            String parentPath = jsonPath.substring(0, jsonPath.lastIndexOf('.'));
            String fieldName = jsonPath.substring(jsonPath.lastIndexOf('.') + 1);

            // Find the parent node
            JsonNode parentNode = JsonPath.using(parseConfigWithJsonNode).parse(root).read(parentPath);

            // Replace the target field in the parent node
            if (parentNode != null && parentNode instanceof ObjectNode) {
                ((ObjectNode) parentNode).replace(fieldName, newNode);
            } else {
                LOG.error("Path does not point to object node");
                throw new IllegalArgumentException("Path does not point to object node");
            }
        } catch (PathNotFoundException e) {
            LOG.error("JsonPath {} not found", jsonPath);
            throw new PathNotFoundException(format("JsonPath {} not found", jsonPath));
        }
    }
}
