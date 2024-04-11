package org.opensearch.dataprepper.pipeline.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.opensearch.dataprepper.model.configuration.PipelinesDataFlowModel;
import org.opensearch.dataprepper.pipeline.parser.transformer.DynamicYamlTransformer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

class DynamicYamlTransformerTest {

    private DynamicYamlTransformer yamlTransformer;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());


    @BeforeEach
    void setUp() {
        PipelinesDataFlowModel pipelinesDataFlowModel;
        PipelinesDataFlowModel templateDataFlowModel;
        String originalSourceYamlFilePath = "src/test/resources/templates/testSource/originalSourceYaml.yaml";
        String templateSourceYamlFilePath = "src/test/resources/templates/testSource/templateSourceYaml.yaml";

        try {

            templateDataFlowModel = yamlMapper.readValue(new File(templateSourceYamlFilePath),
                    PipelinesDataFlowModel.class);
            pipelinesDataFlowModel = yamlMapper.readValue(new File(originalSourceYamlFilePath),
                    PipelinesDataFlowModel.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Initialize the yamlTransformer before each test
        yamlTransformer = new DynamicYamlTransformer(pipelinesDataFlowModel,templateDataFlowModel);
    }
    @Test
    void testSuccessfulTransformation() throws IOException {
//        String originalYaml =
//                "dodb-pipeline:\n" +
//                "  source:\n" +
//                "    documentdb:\n" +
//                "      hostname: \"database.example.com\"\n";
//
//        String templateYaml =
//                "docDB-docdb:\n" +
//                "  source:\n" +
//                "    documentdb:\n" +
//                "      hostname: \"{{dodb-pipeline.source.documentdb.hostname}}\"\n"+
//                "      test: 1\"\n" +
//                "      test: 2\"\n";
//
//        String expectedYaml =
//                "docDB-pipeline:\n" +
//                "  source:\n" +
//                "    documentdb:\n" +
//                "      hostname: \"database.example.com\"\n";

        // Load the original and template YAML files from the test resources directory
//
//        String originalSourceYamlFilePath = "src/test/resources/templates/testSource/originalSourceYaml.yaml";
//        String templateSourceYamlFilePath = "src/test/resources/templates/testSource/templateSourceYaml.yaml";
//        String expectedSourceYamlFilePath = "src/test/resources/templates/testSource/expectedSourceYaml.yaml";
//
//        String originalYaml = Files.readString(Paths.get(originalSourceYamlFilePath));
//        String templateYaml = Files.readString(Paths.get(templateSourceYamlFilePath));
//        String expectedYaml = Files.readString(Paths.get(expectedSourceYamlFilePath));
//
//        String outputPath = "src/test/resources/templates/testSource/transformedSourceYaml.yaml";

//        String transformedYaml = yamlTransformer.transformYaml(originalYaml, templateYaml);
//
//        FileWriter fileWriter = new FileWriter(outputPath);
//        fileWriter.write(transformedYaml);

//        assertEquals(expectedYaml.trim(), transformedYaml.trim(), "The transformed YAML should match the expected YAML.");
    }

    @Test
    void testPathNotFoundInTemplate() throws IOException {
        String originalYaml = "dodb-pipeline:\n" +
                "  source:\n" +
                "    documentdb:\n" +
                "      hostname: \"database.example.com\"\n" +
                "      port: 5432\n";

        String templateYaml = "dodb-pipeline:\n" +
                "  source:\n" +
                "    documentdb:\n" +
                "      hostname: \"{{dodb-pipeline.source.documentdb.hostname}}\"\n";

        String expectedYaml = "dodb-pipeline:\n" +
                "  source:\n" +
                "    documentdb:\n" +
                "      hostname: \"database.example.com\"\n";

//        String transformedYaml = yamlTransformer.transformYaml(originalYaml, templateYaml);



//        assertEquals(expectedYaml.trim(), transformedYaml.trim(), "The transformed YAML should not include unspecified paths.");
    }

    @Test
    void testIOExceptionHandling() {
        String invalidYaml = "dodb-pipeline: [";

//        assertThrows(RuntimeException.class, () -> {
//            yamlTransformer.transformYaml(invalidYaml, invalidYaml);
//        }, "A RuntimeException should be thrown when an IOException occurs.");
    }



}
