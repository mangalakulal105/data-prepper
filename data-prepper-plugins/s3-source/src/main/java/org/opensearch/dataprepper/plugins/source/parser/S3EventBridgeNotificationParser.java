package org.opensearch.dataprepper.plugins.source.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opensearch.dataprepper.plugins.source.S3EventBridgeNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.Message;

public class S3EventBridgeNotificationParser implements S3NotificationParser {
    private static final Logger LOG = LoggerFactory.getLogger(S3EventBridgeNotificationParser.class);
    private static final String DETAIL_KEY = "detail";
    private static final String BUCKET_KEY = "bucket";
    private static final String OBJECT_KEY = "object";
    private static final String RESOURCES_KEY = "resources";

    @Override
    public ParsedMessage parseMessage(final Message message, final ObjectMapper objectMapper) {
        try {
            final JsonNode parsedNode = objectMapper.readTree(message.body());
            if (isEventBridgeNotification(parsedNode)) {
                final S3EventBridgeNotification s3EventBridgeNotification = objectMapper.readValue(message.body(), S3EventBridgeNotification.class);
                return new ParsedMessage(message, s3EventBridgeNotification);
            }
        } catch (final JsonProcessingException e) {
            LOG.error("SQS message with message ID:{} has invalid body which cannot be parsed into EventBridgeNotification. {}.", message.messageId(), e.getMessage());
        }
        return new ParsedMessage(message, true);
    }

    private boolean isEventBridgeNotification(final JsonNode parsedNode) {
        return parsedNode.has(DETAIL_KEY) && parsedNode.has(RESOURCES_KEY)
                && parsedNode.get(DETAIL_KEY).has(BUCKET_KEY) && parsedNode.get(DETAIL_KEY).has(OBJECT_KEY);
    }
}
