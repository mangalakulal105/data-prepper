package org.opensearch.dataprepper.plugins.processor.ruby;

import org.jruby.RubyInstanceConfig;
import org.jruby.embed.ScriptingContainer;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@DataPrepperPlugin(name= "ruby", pluginType = Processor.class, pluginConfigurationType = RubyProcessorConfig.class)
public class RubyProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(RubyProcessor.class);
    private final RubyProcessorConfig config;
    private final String codeToInject;
    private ScriptingContainer container;
    private String script;

    private Boolean runningCodeFromFile = false;

    @DataPrepperPluginConstructor
    public RubyProcessor(final PluginMetrics pluginMetrics, final RubyProcessorConfig config) {
        super(pluginMetrics);
        this.config = config;
        this.codeToInject = config.getCode();

        container = new ScriptingContainer();
        container.setCompileMode(RubyInstanceConfig.CompileMode.JIT);

        container.put("LOG", LOG); // inject logger, perform cold start

        if (config.isInitDefined()) {
            container.runScriptlet(config.getInitCode());
        }

        if (config.isCodeFromFile()) {
            // will throw a RuntimeException if not, but todo: look at file source
            verifyFileExists(config.getPath());
            runningCodeFromFile = true;
            runInitCodeFromFileAndDefineProcessMethod(config.getPath());
//            verifyFileExistsAndContainsProcessMethod(config.getCodeFilePath());
        }
    }

    private void verifyFileExists(final String codeFilePath) {
        LocalInputFile inputFile = new LocalInputFile(codeFilePath);
    }

    private void runInitCodeFromFileAndDefineProcessMethod(final String codeFilePath) {
        // check if init code exists

        // open file read to read InputStream at codeFilePath

        // check if file contains init method (scan until we hit def init)

        // if so, then run the following:
        // scan all into a single String (todo: max String size?)

        // container.runScriplet(this string)

        // then scan process(event) and runScriplet on it.

        // would all need to be in one.
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
        final List<Event> events = records.stream().map(Record::getData).collect(Collectors.toList());

        if (runningCodeFromFile) {
            processEventsWithFileCode(events);
        } else {
            injectAndProcessEvents(events);
        }
        return records;
    }

    private void processEventsWithFileCode(final List<Event> events) {
        container.put("events", events);

        script = "events.each { |event| \n"
                + "process(event)\n" +
                "}";
        // todo: make it like LogStash where it returns an array of events?

        container.runScriptlet(config.getCode());
    }

    private void injectAndProcessEvents(List<Event> events) {
        container.put("events", events);
        script = "events.each { |event| \n"
                + this.codeToInject +
                "}";

        try {
            container.runScriptlet(script);
        } catch (Exception exception) {
            LOG.error("Exception processing Ruby code", exception);
            if (!config.isIgnoreException()) {
                throw new RuntimeException(exception.toString());
            }
        }
    }

    @Override
    public void prepareForShutdown() {

    }

    @Override
    public boolean isReadyForShutdown() {
        return true;
    }

    @Override
    public void shutdown() {
        container.terminate();
    }

    public String getCodeToExecute() {
        return this.script;
    }
}