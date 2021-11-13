package org.opensearch.dataprepper.logstash;

import com.amazon.dataprepper.model.configuration.PipelineModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.opensearch.dataprepper.logstash.mapping.LogstashMapper;
import org.opensearch.dataprepper.logstash.model.LogstashConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * Converts Logstash configuration file and returns YAML file location
 *
 * @since 1.2
 */
public class LogstashConfigConverter {
    public String convertLogstashConfigurationToPipeline(String logstashConfigurationPath, String outputDirectory) throws IOException {
        final Path configurationFilePath = Paths.get(logstashConfigurationPath);
        final String logstashConfigAsString = new String(Files.readAllBytes(configurationFilePath));

        LogstashLexer lexer = new LogstashLexer(CharStreams.fromString(logstashConfigAsString));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LogstashParser parser = new LogstashParser(tokens);
        final ParseTree tree = parser.config();

        org.opensearch.dataprepper.logstash.parser.LogstashVisitor visitor = new org.opensearch.dataprepper.logstash.parser.LogstashVisitor();
        LogstashConfiguration logstashConfiguration = (LogstashConfiguration) visitor.visit(tree);

        LogstashMapper logstashMapper = new LogstashMapper();
        PipelineModel pipelineModel = logstashMapper.mapPipeline(logstashConfiguration);
        final Map<String, Object> pipeline = Collections.singletonMap("logstash-converted-pipeline", pipelineModel);

        ObjectMapper mapper = new ObjectMapper(YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .disable(YAMLGenerator.Feature.SPLIT_LINES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .build());

        final String confFileName = configurationFilePath.getFileName().toString();
        final String yamlFileName = confFileName.substring(0, confFileName.lastIndexOf("."));

        final Path yamlFilePath = Paths.get(outputDirectory , yamlFileName + ".yaml");

        mapper.writeValue(new File(String.valueOf(yamlFilePath)), pipeline);

        return String.valueOf(yamlFilePath);
    }
}
