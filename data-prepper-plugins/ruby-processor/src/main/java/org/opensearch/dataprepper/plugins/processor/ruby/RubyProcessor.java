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


@DataPrepperPlugin(name= "ruby", pluginType = Processor.class, pluginConfigurationType = RubyProcessorConfig.class)
public class RubyProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(RubyProcessor.class);
    private final RubyProcessorConfig config;

    private ScriptingContainer container;
    
    @DataPrepperPluginConstructor
    public RubyProcessor(final PluginMetrics pluginMetrics, final RubyProcessorConfig config) {
        // todo: look into init hooks?
        // validate that if file, contains def process(
        super(pluginMetrics);
        this.config = config;

        container = new ScriptingContainer();
        container.setCompileMode(RubyInstanceConfig.CompileMode.JIT);

        container.put("LOG", LOG); // inject logger, perform cold start
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
        container.runScriptlet("puts 'hello world'");
        return records;
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
}