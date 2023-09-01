package org.opensearch.dataprepper.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.opensearch.dataprepper.model.plugin.PluginConfigValueTranslator;

import java.io.IOException;
import java.lang.annotation.Annotation;

public class TestJackson {

    public static void main(String[] args) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new DataPrepperStringContextualDeserializer(new CustomPluginConfigValueTranslator()));

        objectMapper.registerModule(module);

        String json = "{\"customAttribute\": \"hello world\", \"nonCustomAttribute\": \"hello world\"}";

        MyClass myObject = objectMapper.readValue(json, MyClass.class);
        System.out.println(myObject.customAttribute);
        System.out.println(myObject.nonCustomAttribute);
    }

    static class MyClass {
        @JsonProperty("nonCustomAttribute")
        String nonCustomAttribute;

        @JsonProperty("customAttribute")
        @SupportSecretString
        String customAttribute;

        // Getter and setter for the customAttribute
    }

    static class CustomPluginConfigValueTranslator implements PluginConfigValueTranslator {

        @Override
        public String translate(String value) {
            return getKey() + value;
        }

        @Override
        public String getKey() {
            return "prefix";
        }
    }

    static class CustomStringDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {
        private Annotation annotation;
        private final String attribute;

        public CustomStringDeserializer() {
            attribute = "";
        }

        public CustomStringDeserializer(final String attribute) {
            this.attribute = attribute;
        }

        @Override
        public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException, JsonProcessingException {
            return attribute + jsonParser.getValueAsString();
        }

        @Override
        public JsonDeserializer<String> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
            annotation = property.getAnnotation(Deprecated.class);
            if (annotation != null) {
                return new CustomStringDeserializer("prefix");
            } else {
                return new CustomStringDeserializer();
            }
        }
    }
}
