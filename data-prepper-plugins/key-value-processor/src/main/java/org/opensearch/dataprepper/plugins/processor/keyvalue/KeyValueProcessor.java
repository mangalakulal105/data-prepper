/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.keyvalue;

import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.plugin.InvalidPluginConfigurationException;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.regex.Matcher;
import java.util.Stack;
import java.util.ArrayList;

import static org.opensearch.dataprepper.logging.DataPrepperMarkers.EVENT;

@DataPrepperPlugin(name = "key_value", pluginType = Processor.class, pluginConfigurationType = KeyValueProcessorConfig.class)
public class KeyValueProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(KeyValueProcessor.class);
    private static String startGroupStrings[] = {"\"","'", "(", "[", "<", "{", "http://", "https://"};
    private static Character endGroupChars[] ={'"', '\'', ')', ']', '>', '}', ' ', ' '};

    private final KeyValueProcessorConfig keyValueProcessorConfig;

    private final ExpressionEvaluator expressionEvaluator;

    private final Pattern fieldDelimiterPattern;
    private final Pattern keyValueDelimiterPattern;
    private final Set<String> includeKeysSet = new HashSet<String>();
    private final Set<String> excludeKeysSet = new HashSet<String>();
    private final HashMap<String, Object> defaultValuesMap = new HashMap<>();
    private final Set<String> defaultValuesSet = new HashSet<String>();
    private final String lowercaseKey = "lowercase";
    private final String uppercaseKey = "uppercase";
    private final String capitalizeKey = "capitalize";
    private final Set<String> validTransformOptionSet = Set.of("", lowercaseKey, uppercaseKey, capitalizeKey);
    private final String whitespaceStrict = "strict";
    private final String whitespaceLenient = "lenient";
    private final Set<String> validWhitespaceSet = Set.of(whitespaceLenient, whitespaceStrict);
    final String delimiterBracketCheck = "[\\[\\]()<>]";
    private final Set<Character> bracketSet = Set.of('[', ']', '(', ')', '<', '>');
    private final List<String> tagsOnFailure;

    @DataPrepperPluginConstructor
    public KeyValueProcessor(final PluginMetrics pluginMetrics,
                             final KeyValueProcessorConfig keyValueProcessorConfig,
                             final ExpressionEvaluator expressionEvaluator) {
        super(pluginMetrics);
        this.keyValueProcessorConfig = keyValueProcessorConfig;

        tagsOnFailure = keyValueProcessorConfig.getTagsOnFailure();

        if (keyValueProcessorConfig.getFieldDelimiterRegex() != null
                && !keyValueProcessorConfig.getFieldDelimiterRegex().isEmpty()) {
            if (keyValueProcessorConfig.getFieldSplitCharacters() != null
                && !keyValueProcessorConfig.getFieldSplitCharacters().isEmpty()) {
                throw new IllegalArgumentException("field_delimiter_regex and field_split_characters cannot both be defined.");
            } else if (!validateRegex(keyValueProcessorConfig.getFieldDelimiterRegex())) {
                throw new PatternSyntaxException("field_delimiter_regex is not a valid regex string", keyValueProcessorConfig.getFieldDelimiterRegex(), -1);
            }

            fieldDelimiterPattern = Pattern.compile(keyValueProcessorConfig.getFieldDelimiterRegex());

            if (keyValueProcessorConfig.getRecursive()) {
                if (fieldDelimiterPattern.matcher(delimiterBracketCheck).matches()) {
                    throw new IllegalArgumentException("While recursive is true, the set field delimiter regex cannot contain brackets while you are trying to recurse.");
                }
            }
        } else {
            String regex;
            if (keyValueProcessorConfig.getFieldSplitCharacters().isEmpty()) {
                regex = KeyValueProcessorConfig.DEFAULT_FIELD_SPLIT_CHARACTERS;
            } else {
                if (keyValueProcessorConfig.getRecursive()
                    && keyValueProcessorConfig.getFieldSplitCharacters().length() != 1) {
                    throw new IllegalArgumentException("While recursive is true, the set field split characters is limited to one character only.");
                }
                regex = buildRegexFromCharacters(keyValueProcessorConfig.getFieldSplitCharacters());
            }

            fieldDelimiterPattern = Pattern.compile(regex);

            if (keyValueProcessorConfig.getRecursive()
                && fieldDelimiterPattern.matcher(delimiterBracketCheck).matches()) {
                throw new IllegalArgumentException("While recursive is true, the set field delimiter cannot contain brackets while you are trying to recurse.");
            }
        }

        if (keyValueProcessorConfig.getKeyValueDelimiterRegex() != null
                && !keyValueProcessorConfig.getKeyValueDelimiterRegex().isEmpty()) {
            if (keyValueProcessorConfig.getValueSplitCharacters() != null
                && !keyValueProcessorConfig.getValueSplitCharacters().isEmpty()) {
                throw new IllegalArgumentException("key_value_delimiter_regex and value_split_characters cannot both be defined.");
            } else if (!validateRegex(keyValueProcessorConfig.getKeyValueDelimiterRegex())) {
                throw new PatternSyntaxException("key_value_delimiter_regex is not a valid regex string", keyValueProcessorConfig.getKeyValueDelimiterRegex(), -1);
            }

            keyValueDelimiterPattern = Pattern.compile(keyValueProcessorConfig.getKeyValueDelimiterRegex());

            if (keyValueProcessorConfig.getRecursive()
                && keyValueDelimiterPattern.matcher(delimiterBracketCheck).matches()) {
                throw new IllegalArgumentException("While recursive is true, the set key value delimiter regex cannot contain brackets while you are trying to recurse.");
            }
        } else {
            String regex;
            if (keyValueProcessorConfig.getValueSplitCharacters().isEmpty()) {
                regex = KeyValueProcessorConfig.DEFAULT_VALUE_SPLIT_CHARACTERS;
            } else {
                if (keyValueProcessorConfig.getRecursive()
                    && keyValueProcessorConfig.getValueSplitCharacters().length() != 1) {
                    throw new IllegalArgumentException("While recursive is true, the set value split characters is limited to one character only.");
                }

                regex = buildRegexFromCharacters(keyValueProcessorConfig.getValueSplitCharacters());
            }

            keyValueDelimiterPattern = Pattern.compile(regex);

            if (keyValueProcessorConfig.getRecursive()
                && keyValueDelimiterPattern.matcher(delimiterBracketCheck).matches()) {
                throw new IllegalArgumentException("While recursive is true, the set value split characters cannot contain brackets while you are trying to recurse.");
            }
        }

        if (!validateRegex(keyValueProcessorConfig.getDeleteKeyRegex())) {
            throw new PatternSyntaxException("delete_key_regex is not a valid regex string", keyValueProcessorConfig.getDeleteKeyRegex(), -1);
        }

        if (!validateRegex(keyValueProcessorConfig.getDeleteValueRegex())) {
            throw new PatternSyntaxException("delete_value_regex is not a valid regex string", keyValueProcessorConfig.getDeleteValueRegex(), -1);
        }

        includeKeysSet.addAll(keyValueProcessorConfig.getIncludeKeys());
        excludeKeysSet.addAll(keyValueProcessorConfig.getExcludeKeys());
        defaultValuesMap.putAll(keyValueProcessorConfig.getDefaultValues());
        if (!defaultValuesMap.isEmpty()) {
            defaultValuesSet.addAll(defaultValuesMap.keySet());
        }

        validateKeySets(includeKeysSet, excludeKeysSet, defaultValuesSet);
        
        if (!validTransformOptionSet.contains(keyValueProcessorConfig.getTransformKey())) {
            throw new IllegalArgumentException(String.format("The transform_key value: %s is not a valid option", keyValueProcessorConfig.getTransformKey()));
        }

        if (!(validWhitespaceSet.contains(keyValueProcessorConfig.getWhitespace()))) {
            throw new IllegalArgumentException(String.format("The whitespace value: %s is not a valid option", keyValueProcessorConfig.getWhitespace()));
        }

        final Pattern boolCheck = Pattern.compile("true|false", Pattern.CASE_INSENSITIVE);
        final Matcher duplicateValueBoolMatch = boolCheck.matcher(String.valueOf(keyValueProcessorConfig.getSkipDuplicateValues()));
        final Matcher removeBracketsBoolMatch = boolCheck.matcher(String.valueOf(keyValueProcessorConfig.getRemoveBrackets()));
        final Matcher recursiveBoolMatch = boolCheck.matcher(String.valueOf(keyValueProcessorConfig.getRecursive()));

        if (!duplicateValueBoolMatch.matches()) {
            throw new IllegalArgumentException(String.format("The skip_duplicate_values value must be either true or false", keyValueProcessorConfig.getSkipDuplicateValues()));
        }

        if (!removeBracketsBoolMatch.matches()) {
            throw new IllegalArgumentException(String.format("The remove_brackets value must be either true or false", keyValueProcessorConfig.getRemoveBrackets()));
        }

        if (!recursiveBoolMatch.matches()) {
            throw new IllegalArgumentException(String.format("The recursive value must be either true or false", keyValueProcessorConfig.getRemoveBrackets()));
        }

        if (keyValueProcessorConfig.getRemoveBrackets() && keyValueProcessorConfig.getRecursive()) {
            throw new IllegalArgumentException("Cannot remove brackets needed for determining levels of recursion");
        }

        this.expressionEvaluator = expressionEvaluator;
        if (keyValueProcessorConfig.getKeyValueWhen() != null
                && !expressionEvaluator.isValidExpressionStatement(keyValueProcessorConfig.getKeyValueWhen())) {
            throw new InvalidPluginConfigurationException(
                    String.format("key_value_when %s is not a valid expression statement", keyValueProcessorConfig.getKeyValueWhen()));
        }
    }

    private String buildRegexFromCharacters(String s) {
        char[] splitters = s.toCharArray();
        StringBuilder regexedFieldSplitCharacters = new StringBuilder();
        for(char c : splitters) {
            if (Objects.equals(c, '\\')) {
                regexedFieldSplitCharacters.append(c);
            } else {
                regexedFieldSplitCharacters.append(c).append('|');
            }
        }

        regexedFieldSplitCharacters = new StringBuilder(regexedFieldSplitCharacters.substring(0, regexedFieldSplitCharacters.length() - 1));

        return regexedFieldSplitCharacters.toString();
    }

    private boolean validateRegex(final String pattern)
    {
        if (pattern != null && !Objects.equals(pattern, "")) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        return true;
    }

    private void validateKeySets(final Set<String> includeSet, final Set<String> excludeSet, final Set<String> defaultSet) {
        final Set<String> includeIntersectionSet = new HashSet<>(includeSet);
        final Set<String> defaultIntersectionSet = new HashSet<>(defaultSet);

        includeIntersectionSet.retainAll(excludeSet);
        if (!includeIntersectionSet.isEmpty()) {
            throw new IllegalArgumentException("Include keys and exclude keys set cannot have any overlap");
        }

        defaultIntersectionSet.retainAll(excludeSet);
        if (!defaultIntersectionSet.isEmpty()) {
            throw new IllegalArgumentException("Cannot exclude a default key!");
        }
    }

    private boolean isEscapedQuote(final String str, int idx) {
        if (idx > 0) {
            if ((str.charAt(idx) == '"' || str.charAt(idx) == '\'') &&
                (str.charAt(idx-1) == '\\'))
                return true;
        }
        return false;
    }

    public int skipGroup(final String str, int idx, final Character endChar) {
        int i = idx;
        while (i < str.length()) {
            if (isEscapedQuote(str, i)) {
                i++;
                continue;
            } else if (str.charAt(i) == endChar) {
                return i-1;
            } else
                i++;
        }
        throw new RuntimeException("Bad Input, no end character found in "+str+" after index " + idx +", expected end char = "+endChar);
    }

    private void addPart(List<String> parts, final String str, final int start, final int end) {
        String part = str.substring(start,end).trim();
        if (part.length() > 0) {
            parts.add(part);
        }
    }

    public int findInStartGroup(final String str, int idx) {
        int j = 0;
        while (j < startGroupStrings.length) {
            try {
                if (startGroupStrings[j].equals(str.substring(idx, idx+startGroupStrings[j].length()))) {
                    if (j <= 1 && idx > 0 && str.charAt(idx-1) != '\\') {
                        return j;
                    } else if (j > 1) {
                        return j;
                    }
                }
            } catch (Exception e) {
                return -1;
            }
            j++;
        }
        return -1;
    }
    
    private List<String> parseWithValueGrouping(String str) {
        String fieldDelimiter = keyValueProcessorConfig.getFieldSplitCharacters();
        Set<Character> fieldDelimiterSet = new HashSet<>();
        for (char ch : fieldDelimiter.toCharArray()) {
            fieldDelimiterSet.add(ch);
        }
        int i = 0;
        int start = i;
        List<String> parts = new ArrayList<>();
        while (i < str.length()) {
            // checks if a " or ' is escaped using \, if yes, it is
            // not considered as a start of a group
            if (isEscapedQuote(str, i)) {
                i++;
                continue;
            }
            int groupIndex = findInStartGroup(str, i);
            if (groupIndex >= 0) {
                i = skipGroup(str, i+1, endGroupChars[groupIndex])+2;
            } else if (fieldDelimiterSet.contains(str.charAt(i))) {
                addPart(parts, str, start, i);
                i++;
                start = i;
            } else {
               i++;
            }
        }
        if (start != i) {
            addPart(parts, str, start, i);
        }
        
        return parts;
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
        final ObjectMapper mapper = new ObjectMapper();

        for (final Record<Event> record : records) {
            final Map<String, Object> outputMap = new HashMap<>();
            final Event recordEvent = record.getData();

            try {

                if (keyValueProcessorConfig.getKeyValueWhen() != null && !expressionEvaluator.evaluateConditional(keyValueProcessorConfig.getKeyValueWhen(), recordEvent)) {
                    continue;
                }

                final String groupsRaw = recordEvent.get(keyValueProcessorConfig.getSource(), String.class);
                if (groupsRaw == null) {
                    continue;
                }
                String[] groups;
                if (keyValueProcessorConfig.getValueGrouping()) {
                    groups = parseWithValueGrouping(groupsRaw).stream().toArray(String[]::new);
                } else {
                    groups = fieldDelimiterPattern.split(groupsRaw, 0);
                }

                if (keyValueProcessorConfig.getRecursive()) {
                    try {
                        JsonNode recursedTree = recurse(groupsRaw, mapper);
                        outputMap.putAll(createRecursedMap(recursedTree, mapper));
                    } catch (Exception e) {
                        LOG.error("Recursive parsing ran into an unexpected error, treating message as non-recursive", e);
                        recordEvent.getMetadata().addTags(tagsOnFailure);
                    }
                } else {
                    try {
                        outputMap.putAll(createNonRecursedMap(groups));
                    } catch (Exception e) {
                        LOG.error("Non-recursive parsing ran into an unexpected error", e);
                        recordEvent.getMetadata().addTags(tagsOnFailure);
                    }
                }

                final Map<String, Object> processedMap = executeConfigs(outputMap);

                if (Objects.isNull(keyValueProcessorConfig.getDestination())) {
                    writeToRoot(recordEvent, processedMap);
                } else {
                    if (keyValueProcessorConfig.getOverwriteIfDestinationExists() ||
                            !recordEvent.containsKey(keyValueProcessorConfig.getDestination())) {
                        recordEvent.put(keyValueProcessorConfig.getDestination(), processedMap);
                    }
                }
            } catch (final Exception e) {
                LOG.error(EVENT, "There was an exception while processing on Event [{}]: ", recordEvent, e);
                recordEvent.getMetadata().addTags(tagsOnFailure);
            }
        }

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
    }

    private ObjectNode recurse(final String input, final ObjectMapper mapper) {
        Stack<Character> bracketStack = new Stack<>();
        Map<Character, Character> bracketMap = initBracketMap();
        int pairStart = 0;

        ArrayList<String> pairs = new ArrayList<>();
        ObjectNode root = mapper.createObjectNode();

        for (int i = 0; i < input.length(); i++) {
            if (bracketMap.containsKey(input.charAt(i))) {
                bracketStack.push(input.charAt(i));
            }

            if (bracketMap.containsValue(input.charAt(i)) && !bracketStack.isEmpty()) {
                if (bracketMap.get(bracketStack.peek()) == input.charAt(i)) {
                    bracketStack.pop();
                }
            }

            if (bracketStack.isEmpty() && fieldDelimiterPattern.matcher(String.valueOf(input.charAt(i))).matches()) {
                String pair = input.substring(pairStart, i);
                pairs.add(pair);
                pairStart = i + 1;
            }
        }

        pairs.add(input.substring(pairStart));

        for (final String pair : pairs) {
            int keyStart = 0;
            int keyEnd = -1;
            int valueStart = -1;
            int valueEnd = -1;
            String keyString = "";
            String valueString;
            final Character whitespaceChar = ' ';

            bracketStack.clear();

            for (int i = 0; i < pair.length(); i++) {
                if (bracketStack.isEmpty() && keyValueDelimiterPattern.matcher(String.valueOf(pair.charAt(i))).matches()) {
                    keyString = pair.substring(keyStart, i).stripTrailing();
                    valueStart = i + 1;
                    while(pair.charAt(valueStart) == whitespaceChar) {
                        valueStart++;
                    }
                    break;
                }
            }

            if (keyString.isBlank()) {
                keyString = pair;
                LOG.debug("Unsuccessful match: '{}'", keyString);
                valueString = keyValueProcessorConfig.getNonMatchValue().toString().stripLeading();
            } else if (bracketMap.containsKey(pair.charAt(valueStart))) {
                if (pair.charAt(pair.length() - 1) == bracketMap.get(pair.charAt(valueStart))) {
                    valueStart++;
                    valueEnd = pair.length() - 1;
                    valueString = pair.substring(valueStart, valueEnd).stripLeading();
                    JsonNode child = ((ObjectNode) root).put(keyString, recurse(valueString, mapper));
                } 
            } else {
                valueString = pair.substring(valueStart).stripLeading();
                ObjectNode child = ((ObjectNode)root).put(keyString, valueString);
            }
        }

        return root;
    }

    private static Map<Character, Character> initBracketMap() {
        Map<Character, Character> bracketMap = new HashMap<>();

        bracketMap.put('[', ']');
        bracketMap.put('(', ')');
        bracketMap.put('<', '>');

        return bracketMap;
    }

    private Map<String, Object> createRecursedMap(JsonNode node, ObjectMapper mapper) {
        return mapper.convertValue(node, new TypeReference<HashMap<String, Object>>() {});
    }

    private Map<String, Object> createNonRecursedMap(String[] groups) {
        Map<String, Object> nonRecursedMap = new LinkedHashMap<>();
        List<Object> valueList;

        for(final String group : groups) {
            final String[] terms = keyValueDelimiterPattern.split(group, 2);
            String key = terms[0];
            Object value;
            
            if (terms.length == 2) {
                value = terms[1];
            } else {
                LOG.debug("Unsuccessful match: '{}'", terms[0]);
                value = keyValueProcessorConfig.getNonMatchValue();
            }

            if (nonRecursedMap.containsKey(key)) {
                Object existingValue = nonRecursedMap.get(key);

                if (existingValue instanceof List) {
                    valueList = (List<Object>) existingValue;
                } else {
                    valueList = new ArrayList<Object>();
                    valueList.add(existingValue);
                    nonRecursedMap.put(key, valueList);
                }

                if (keyValueProcessorConfig.getSkipDuplicateValues()) {
                    if (!valueList.contains(value)) {
                        valueList.add(value);
                    }
                } else {
                    valueList.add(value);
                }
            } else {
                nonRecursedMap.put(key, value);
            }
        }

        return nonRecursedMap;
    }

    private Map<String, Object> executeConfigs(Map<String, Object> map) {
        Map<String, Object> processed = new HashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!includeKeysSet.isEmpty() && !includeKeysSet.contains(key)) {
                LOG.debug("Skipping not included key: '{}'", key);
                continue;
            }

            if (excludeKeysSet.contains(key)) {
                LOG.debug("Key is being excluded: '{}'", key);
                continue;
            }

            if (keyValueProcessorConfig.getDeleteKeyRegex() != null && !Objects.equals(keyValueProcessorConfig.getDeleteKeyRegex(), "")) {
                key = key.replaceAll(keyValueProcessorConfig.getDeleteKeyRegex(), "");
            }
            key = keyValueProcessorConfig.getPrefix() + key;

            if (value != null
                    && value instanceof String
                    && keyValueProcessorConfig.getDeleteValueRegex() != null
                    && !Objects.equals(keyValueProcessorConfig.getDeleteValueRegex(), "")) {
                value = ((String)value).replaceAll(keyValueProcessorConfig.getDeleteValueRegex(), "");
            }

            if (keyValueProcessorConfig.getWhitespace().equals(whitespaceStrict)) {
                String[] whitespace_arr = trimWhitespace(key, value);
                key = whitespace_arr[0];
                value = whitespace_arr[1];
            }

            if (keyValueProcessorConfig.getTransformKey() != null
                    && !keyValueProcessorConfig.getTransformKey().isEmpty()) {
                key = transformKey(key);
            }

            if (keyValueProcessorConfig.getRemoveBrackets()) {
                final String bracketRegex = "[\\[\\]()<>]";
                if (value != null) {
                    value = value.toString().replaceAll(bracketRegex,"");
                }
            }

            addKeyValueToMap(processed, key, value);
        }

        for (Map.Entry<String,Object> pair : defaultValuesMap.entrySet()) {
            if (processed.containsKey(pair.getKey())) {
                LOG.debug("Skipping already included default key: '{}'", pair.getKey());
                continue;
            }
            processed.put(pair.getKey(), pair.getValue());
        }

        if (keyValueProcessorConfig.getDropKeysWithNoValue()) {
            processed.entrySet().removeIf(entry -> entry.getValue() == null);
        }
        return processed;
    }

    private String[] trimWhitespace(String key, Object value) {
        String[] arr = {key.stripTrailing(), value.toString().stripLeading()};
        return arr;
    }
    
    private String transformKey(String key) {
        if (keyValueProcessorConfig.getTransformKey().equals(lowercaseKey)) {
            key = key.toLowerCase();
        } else if (keyValueProcessorConfig.getTransformKey().equals(capitalizeKey)) {
            key = key.substring(0, 1).toUpperCase() + key.substring(1);
        } else if (keyValueProcessorConfig.getTransformKey().equals(uppercaseKey)) {
            key = key.toUpperCase();
        }
        return key;
    }

    private void addKeyValueToMap(final Map<String, Object> parsedMap, final String key, Object value) {
        Object processedValue = value;

        if (value instanceof List) {
            List<?> valueAsList = (List<?>) value;
            if (valueAsList.size() == 1) {
                processedValue = valueAsList.get(0);
            }
        }

        if (!parsedMap.containsKey(key)) {
            parsedMap.put(key, processedValue);
            return;
        }

        if (parsedMap.get(key) instanceof List) {
            if (keyValueProcessorConfig.getSkipDuplicateValues()) {
                if (((List<Object>) parsedMap.get(key)).contains(processedValue)) {
                    return;
                }
            }

            ((List<Object>) parsedMap.get(key)).add(processedValue);
        } else {
            if (keyValueProcessorConfig.getSkipDuplicateValues()) {
                if (parsedMap.containsValue(processedValue)) {
                    return;
                }
            }

            final LinkedList<Object> combinedList = new LinkedList<>();
            combinedList.add(parsedMap.get(key));
            combinedList.add(processedValue);

            parsedMap.replace(key, combinedList);
        }
    }

    private void writeToRoot(final Event event, final Map<String, Object> parsedJson) {
        for (Map.Entry<String, Object> entry : parsedJson.entrySet()) {
            if (keyValueProcessorConfig.getOverwriteIfDestinationExists() || !event.containsKey(entry.getKey())) {
                event.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
