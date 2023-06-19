package org.opensearch.dataprepper.plugins.processor.ruby;

import org.checkerframework.checker.units.qual.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.source.loggenerator.ApacheLogFaker;
import org.opensearch.dataprepper.plugins.source.loggenerator.LogGeneratorSource;
import org.opensearch.dataprepper.plugins.source.loggenerator.logtypes.CommonApacheLogTypeGenerator;
import org.opensearch.dataprepper.metrics.PluginMetrics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opensearch.dataprepper.test.helper.ReflectivelySetField.setField;

public class RubyProcessorIT {
    private static final int NUMBER_EVENTS_TO_TEST = 100;
    private static final String PLUGIN_NAME = "ruby";
    private static final String TEST_PIPELINE_NAME = "ruby_processor_test";

    static String CODE_STRING = "require 'java'\n" + "puts event.class\n" +
            "event.put('downcase', event.get('message').downcase)";

    private RubyProcessor rubyProcessor;

    private RubyProcessorConfig rubyProcessorConfig;
    private CommonApacheLogTypeGenerator apacheLogGenerator;

    @BeforeEach
    void setup() {
        rubyProcessorConfig = new RubyProcessorConfig();
        setRubyProcessorConfigField("code", CODE_STRING);

        apacheLogGenerator = new CommonApacheLogTypeGenerator();

        PluginMetrics pluginMetrics = PluginMetrics.fromNames(PLUGIN_NAME, TEST_PIPELINE_NAME);

        rubyProcessor = new RubyProcessor(pluginMetrics, rubyProcessorConfig);
    }

    @AfterEach
    void tearDown() {
        rubyProcessor.shutdown();
    }

    @Test
    void rubyProcessorWithFileIntegrationTest()
            throws IOException {
        // create input file
        final int COUNTER_OFFSET = 1;
        final Map<String, String> params = Map.of("counter_start", String.valueOf(COUNTER_OFFSET));

        final String code = "def init(params)\n" +
                "  $counter = params.get('counter_start').to_i\n" +
                "end\n" +
                "def process(event)\n" +
                " event.put('counter', $counter)\n" +
                " $counter += 1\n" +
                "end\n";

        String testDataFilePath = "LocalInputFileTest";

        File testDataFile = File.createTempFile(testDataFilePath, "rb");

        writeRubyCodeToFile(testDataFile, code);

        setRubyProcessorConfigField("path", testDataFile.getAbsolutePath());
        setRubyProcessorConfigField("params", params);

        spinUpNewRubyProcessor();

        final List<Record<Event>> records = getSampleEventLogs();
        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < records.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("counter", Integer.class), equalTo(COUNTER_OFFSET + recordNumber));

            final String originalString = parsedEvent.get("message", String.class);
            assertThat(records.get(recordNumber).getData().get("message", String.class), equalTo(originalString));
        }
    }

    @Test
    void when_eventMessageIsDowncasedByRubyProcessor_then_eventMessageIsDowncased() {
        final List<Record<Event>> records = getSampleEventLogs();
        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < records.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            final String originalString = parsedEvent.get("message", String.class);
            assertThat(parsedEvent.get("downcase", String.class), equalTo(originalString.toLowerCase()));
        }
    }

    private List<Record<Event>> getSampleEventLogs() {
        final List<Record<Event>> records = IntStream.range(0, NUMBER_EVENTS_TO_TEST)
                .mapToObj(i -> new Record<>(apacheLogGenerator.generateEvent()))
                .collect(Collectors.toList());
        return records;
    }

    @Test
    void when_needToCleanScriptAPICallsAndJavaAlreadyRequired_then_works() {
        final List<Record<Event>> records = getSampleEventLogs();

        CODE_STRING = "require 'java'\n" + "puts event.class\n" +
                "event.put('downcase', event.get('message').downcase)";
        setup();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < records.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            final String originalString = parsedEvent.get("message", String.class);
            assertThat(parsedEvent.get("downcase", String.class), equalTo(originalString.toLowerCase()));
        }
    }

    @Test
    void when_needToCleanScriptAPICallsAndJavaNotRequired_then_injectsJavaAndWorks() {
        final List<Record<Event>> records = getSampleEventLogs();

        CODE_STRING = "event.put('downcase', event.get('message').downcase)";
        setup();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < records.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            final String originalString = parsedEvent.get("message", String.class);
            assertThat(parsedEvent.get("downcase", String.class), equalTo(originalString.toLowerCase()));
        }
    }

    @Test
    void when_dotClassMethodCalledAnywhereBesidesEventGetCall_then_remainsUnchanged() {
        final List<Record<Event>> records = getSampleEventLogs();

        CODE_STRING = "puts event.class\n" +
                "event.put('downcase', event.get('message').downcase)";

        setup();
        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        assertThat(rubyProcessor.getCodeToExecute().contains("puts event.class"), equalTo(true));
        // todo: will require a refactor to assert
    }

    @Test
    void when_eventAPICallsOverriddenOnString_then_returnedObjectDoesNotHaveArrayFunctionality // or "need not be typecast" or some variation
    () {
        // create event with string field
        final List<Record<Event>> records = getSampleEventLogs();

        // call get on the event field.
        CODE_STRING = "out_str = event.get('message')\n" +
                "event.put('message_is_array_and_includes_x', out_str.include?('x')";
        setup();

        assertThrows(Exception.class, () -> rubyProcessor.doExecute(records)); // todo: more descriptive exception
    }

    @Test
    void when_messageFieldIsAnArrayPrimitiveOfObjects_then_rubyArrayOperationsWork() { // todo: Test on actual primitives.
        final List<Record<Event>> records = getSampleEventLogs();

        for (Record record : records) {
            Event event = (Event) record.getData();
            String[] arrayToPut = {"x", "y", "z"};
            event.put("message", arrayToPut);
        }
        CODE_STRING = "out_arr = event.get('message')\n" +
                "event.put('message_is_array_and_includes_x', out_arr.include?('x'))\n";
        setup();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < records.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("message_is_array_and_includes_x", Boolean.class), equalTo(true));
        }
    }

    @Test
    void when_rubyProcessingException_then_eventsAreTagged() {
        // todo
    }

    @Test
    void when_messageFieldIsAnArrayPrimitiveOfPrimitives_then_rubyArrayOperationsWork() {
        final List<Record<Event>> records = getSampleEventLogs();
        final int intOne = 3;
        final int intTwo = -5;
        for (Record record : records) {
            Event event = (Event) record.getData();
            int[] arrayToPut = {intOne, intTwo};
            event.put("message", arrayToPut);
        }

        CODE_STRING = "out_arr = event.getList('message')\n" + // also works with get()
                "event.put('sum', out_arr.get(0) + out_arr.get(1))\n";
        // todo: document that the java, not ruby, list is returned when calling Event.get().
        setup();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < records.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("sum", Integer.class), equalTo(intOne + intTwo));
        }
    }

    @Test
    void TestArrayListOnObjects() {
        final List<Record<Event>> records = IntStream.range(0, NUMBER_EVENTS_TO_TEST)
                .mapToObj(i -> new Record<>(apacheLogGenerator.generateEvent()))
                .collect(Collectors.toList());
        for (Record record : records) {
            Event event = (Event) record.getData();
            ArrayList<String> arrayToPut = new ArrayList<>();
            arrayToPut.add("x");
            arrayToPut.add("y");
            arrayToPut.add("z");
            event.put("message", arrayToPut);
        }
        CODE_STRING = "out_arr = event.get('message')\n" +
                "event.put('message_is_array_and_includes_x', out_arr.include?('x'))\n";
        setup();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < records.size(); recordNumber++) {

        }


    } // todo: test array list

    @Test
    void when_customObjectIsDefinedInRuby_then_itsMethodsCanBeCalledWithinRubyAndAddedToEvent() {
        final List<Record<Event>> records = getSampleEventLogs();

        CODE_STRING = "event.put('dummy_class_value', $d.get(0) )\n";
        setup();

        String initString = "class DummyClass\n" +
                "  def initialize(value)\n" +
                "    @value = value\n" +
                "  end\n" +
                "\n" +
                "  def get(to_add)\n" +
                "    to_add + @value\n" +
                "  end\n" +
                "end\n" +
                "$d = DummyClass.new(3)\n";
        setRubyProcessorConfigField("initCode", initString);

        PluginMetrics pluginMetrics = PluginMetrics.fromNames(PLUGIN_NAME, TEST_PIPELINE_NAME);

        rubyProcessor = new RubyProcessor(pluginMetrics, rubyProcessorConfig);

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < parsedRecords.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("dummy_class_value", Integer.class), equalTo(3));
        }
    }

    @Test
    void when_listCreatedInRuby_then_listRetrievableInJava() {
        final List<Record<Event>> records = getSampleEventLogs();
        final int intOne = 3;
        final int intTwo = -5;
        for (Record record : records) {
            Event event = (Event) record.getData();
            event.put("intOne", intOne);
            event.put("intTwo", intTwo);
        }

        CODE_STRING = "event.put('out_list', [event.get('intOne'), event.get('intTwo')])\n";

        setup();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);


        for (int recordNumber = 0; recordNumber < parsedRecords.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.getList("out_list", Integer.class), equalTo(Arrays.asList(intOne, intTwo)));
        }
    }

    @Test
    void when_timeObjectUsedInRuby() {
        // todo: dealing with Time in Ruby might be dicey.
    }

    @Test
    void when_customClassDefinedInInitCode_then_rubyClassUsableInEvents() {

    }

    @Test
    void when_requireUsedInInit_then_packageUsableInEvents() {
        CODE_STRING = "event.put('date', Date.new(2023, 6, 12))\n";
        setup();
        final String initString = "require 'date'";

        setRubyProcessorConfigField("initCode", initString);

        PluginMetrics pluginMetrics = PluginMetrics.fromNames(PLUGIN_NAME, TEST_PIPELINE_NAME);

        rubyProcessor = new RubyProcessor(pluginMetrics, rubyProcessorConfig);


        final List<Record<Event>> records = getSampleEventLogs();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < parsedRecords.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("date", Calendar.class).get(Calendar.YEAR),
                    equalTo(2023));
            assertThat(parsedEvent.get("date", Calendar.class).get(Calendar.DAY_OF_MONTH),
                    equalTo(12));

            // todo: make the dates generic.
        }

    }

    @Test
    void when_codeFromFileAndContainsProcessMethodWithCorrectSignature_then_processorWorks()
            throws IOException {
        String code = "def process(event)\n" +
                "event.put('processed', true)\n" +
                "end\n";

        final List<Record<Event>> parsedRecords = runGenericStartupCode(code);
        for (int recordNumber = 0; recordNumber < parsedRecords.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("processed", Boolean.class), equalTo(true));
        }
    }

    @Test
    void when_codeFromFileWithInitAndParams_then_processorWorks()
            throws IOException {


        String code = "def init(params)\n" +
                "$global_var = params.get('message_to_write')\n" +
                "end\n" +
                "def process(event)\n" +
                "event.put('processed', true)\n" +
                "event.put('message_written', $global_var)\n" +
                "end\n";

        Map<String, String> params = Map.of("message_to_write", "hello world");

        String testDataFilePath = "LocalInputFileTest";

        File testDataFile = File.createTempFile(testDataFilePath, "rb");

        writeRubyCodeToFile(testDataFile, code);

        setup();

        setRubyProcessorConfigField("path", testDataFile.getAbsolutePath());

        setRubyProcessorConfigField("params", params);

        PluginMetrics pluginMetrics = PluginMetrics.fromNames(PLUGIN_NAME, TEST_PIPELINE_NAME);

        rubyProcessor = new RubyProcessor(pluginMetrics, rubyProcessorConfig);

        final List<Record<Event>> records = getSampleEventLogs();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);


        for (int recordNumber = 0; recordNumber < parsedRecords.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("processed", Boolean.class), equalTo(true));
            assertThat(parsedEvent.get("message_written", String.class), equalTo("hello world"));
        }

    }


    // todo: test params when assigned to global var can be used within process
    @Test
    void when_codeFromFileAndDoesNotContainProcessMethod_then_exceptionThrown()
            throws IOException {
        String code = "def init\n" +
                "end\n";
        assertThrows(RuntimeException.class, () -> runGenericStartupCode(code));
    }

    @Test
    void when_codeFromFileAndContainsTooManyInitParameters_then_exceptionThrown()
            throws IOException {
        String code = "def init(params1, params2)\n" +
                "end\n"
                + "def process(event)\n" +
                "end\n";
        assertThrows(RuntimeException.class, () -> runGenericStartupCode(code));
    }

    @Test
    void when_codeFromFileAndContainsProcessMethodNoParameters_then_exceptionThrown()
            throws IOException {
        String code = "def process\n" +
                "end\n";

        assertThrows(RuntimeException.class, () -> runGenericStartupCode(code));
    }

    @Test
    void when_codeFromFileAndContainsProcessMethodWithTooManyParameters_then_exceptionThrown()
            throws IOException {
        String code = "def process(event, event2)\n" +
                "end\n";

        assertThrows(RuntimeException.class, () -> runGenericStartupCode(code));
    }

    @Test
    void when_withConfigCodeAndParamsCalledInProcess_then_exceptionThrown() {
        rubyProcessorConfig = new RubyProcessorConfig();

        String code =
                "puts params.get('message_to_write')\n" +
                        "event.put('processed', true)\n" +
                        "event.put('message_written', $global_var)\n";
        setRubyProcessorConfigField("code", code);

        Map<String, String> params = Map.of("message_to_write", "hello world");

        setRubyProcessorConfigField("params", params);
        spinUpNewRubyProcessor();

        final List<Record<Event>> records = getSampleEventLogs();

        assertThrows(Exception.class, () -> rubyProcessor.doExecute(records));

    }


    // todo: assert test params not accessible outside method
    @Test
    void when_withFileAndParamsCalledInProcess_then_exceptionThrown()
            throws IOException {


        String code = "def init(params)\n" +
                "$global_var = params.get('message_to_write')\n" +
                "end\n" +
                "def process(event)\n" +
                "puts params.get('message_to_write')\n" +
                "event.put('processed', true)\n" +
                "event.put('message_written', $global_var)\n" +
                "end\n";

        Map<String, String> params = Map.of("message_to_write", "hello world");

        String testDataFilePath = "LocalInputFileTest";

        File testDataFile = File.createTempFile(testDataFilePath, "rb");

        writeRubyCodeToFile(testDataFile, code);

        setup();

        setRubyProcessorConfigField("path", testDataFile.getAbsolutePath());

        setRubyProcessorConfigField("params", params);
        spinUpNewRubyProcessor();

        final List<Record<Event>> records = getSampleEventLogs();

        assertThrows(Exception.class, () -> rubyProcessor.doExecute(records));
    }

    @Test
    void when_paramsThenAccessibleWithRubyHashIndexing() {
        // todo: or should behavior just be java-like?
    }

    @Test
    void when_initCodeDefinedInConfigWithParams_then_paramsAccessible() {

        String initCode =
                "$global_var = params.get('message_to_write')\n";


        String code = "event.put('processed', true)\n" +
                "event.put('message_written', $global_var)\n";


        Map<String, String> params = Map.of("message_to_write", "hello world");

        setRubyProcessorConfigField("initCode", initCode);

        setRubyProcessorConfigField("code", code);

        setRubyProcessorConfigField("params", params);

        spinUpNewRubyProcessor();

        final List<Record<Event>> records = getSampleEventLogs();
        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);

        for (int recordNumber = 0; recordNumber < parsedRecords.size(); recordNumber++) {
            final Event parsedEvent = parsedRecords.get(recordNumber).getData();
            assertThat(parsedEvent.get("processed", Boolean.class), equalTo(true));
            assertThat(parsedEvent.get("message_written", String.class), equalTo("hello world"));
        }
    }

    @ParameterizedTest
    @CsvSource(value= {
            "def init(params)end | true",
            "def init(parameter_name) | true",
            "def init(parameter_name, parameter_name2) | false",
            "def init | false",
            "def init() | false",
            "def init(1h) | false",
            "def init(_local) | true"
    }, delimiter = '|')
    void utility_testThatInitRegexMatchesWithConfig(String rubyCode, boolean expectedRegexMatchValue) {
        Pattern compiledPattern = Pattern.compile(RubyProcessor.RUBY_METHOD_INIT_PATTERN);

        Matcher matcher = compiledPattern.matcher(rubyCode);
        assertThat(matcher.find(), equalTo(expectedRegexMatchValue));
    }

    @ParameterizedTest
    @CsvSource(value={
            "def process(parameter_name) | true",
            "def process(parameter_name, parameter_name2) | false",
            "def process | false",
            "def process() | false",
            "def process(1h) | false",
            "def process(event) | true"
    },  delimiter = '|')
    void utility_testThatProcessRegexMatches(String rubyCode, boolean expectedRegexMatchValue) {

        Pattern compiledPattern = Pattern.compile(RubyProcessor.RUBY_METHOD_PROCESS_PATTERN);

        Matcher matcher = compiledPattern.matcher(rubyCode);
        assertThat(matcher.find(), equalTo(expectedRegexMatchValue));
    }
    private List<Record<Event>> runGenericStartupCode(String code) throws IOException {

        String testDataFilePath = "LocalInputFileTest";

        File testDataFile = File.createTempFile(testDataFilePath, "rb");

        writeRubyCodeToFile(testDataFile, code);

        setup();

        setRubyProcessorConfigField("path", testDataFile.getAbsolutePath());

        PluginMetrics pluginMetrics = PluginMetrics.fromNames(PLUGIN_NAME, TEST_PIPELINE_NAME);

        rubyProcessor = new RubyProcessor(pluginMetrics, rubyProcessorConfig);

        final List<Record<Event>> records = getSampleEventLogs();

        final List<Record<Event>> parsedRecords = (List<Record<Event>>) rubyProcessor.doExecute(records);
        return parsedRecords;
    }

    private void writeRubyCodeToFile(File file, String code) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write(code);
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
        }
    }

    private void setRubyProcessorConfigField(String fieldName, Object value) {
        try {
            setField(RubyProcessorConfig.class, RubyProcessorIT.this.rubyProcessorConfig, fieldName, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void spinUpNewRubyProcessor() {
        apacheLogGenerator = new CommonApacheLogTypeGenerator();

        PluginMetrics pluginMetrics = PluginMetrics.fromNames(PLUGIN_NAME, TEST_PIPELINE_NAME);

        rubyProcessor = new RubyProcessor(pluginMetrics, rubyProcessorConfig);
    }
}
