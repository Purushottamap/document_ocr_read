package com.affordplan.documentstatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Lambda's SQS event into validated retry messages. */
final class SqsMessageReader {
    private final ObjectMapper json;

    SqsMessageReader(ObjectMapper json) {
        this.json = json;
    }

    List<RetryMessage> read(Map<String, Object> event) throws Exception {
        Object recordsValue = event.get(StringConstant.SQS_RECORDS_FIELD);
        if (!(recordsValue instanceof Iterable<?> records)) {
            throw new IllegalArgumentException(StringConstant.INVALID_SQS_EVENT);
        }
        List<RetryMessage> messages = new ArrayList<>();
        for (Object item : records) {
            if (!(item instanceof Map<?, ?> record)
                    || !(record.get(StringConstant.SQS_BODY_FIELD) instanceof String body)) {
                throw new IllegalArgumentException(StringConstant.INVALID_SQS_RECORD);
            }
            messages.add(parse(body));
        }
        return messages;
    }

    private RetryMessage parse(String body) throws Exception {
        if (body.stripLeading().startsWith(StringConstant.JSON_OBJECT_START)) {
            JsonNode jsonMessage = json.readTree(body);
            String id = jsonMessage.path(StringConstant.DOCUMENT_ID_FIELD).asText();
            if (id.isBlank()) throw new IllegalArgumentException(StringConstant.MISSING_DOCUMENT_ID);
            int count = Math.max(0, jsonMessage.path(StringConstant.RETRY_COUNT_FIELD).asInt(0));
            JsonNode retryAt = jsonMessage.path(StringConstant.NEXT_RETRY_AT_FIELD);
            return new RetryMessage(id, count, retryAt.isTextual() ? Instant.parse(retryAt.asText()) : null);
        }
        if (body.isBlank()) throw new IllegalArgumentException(StringConstant.EMPTY_MESSAGE_BODY);
        return new RetryMessage(body, 0, null);
    }
}
