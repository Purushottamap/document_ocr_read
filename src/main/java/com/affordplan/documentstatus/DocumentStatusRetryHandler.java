package com.affordplan.documentstatus;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Checks document API status for SQS messages and schedules pending documents for another check. */
public class DocumentStatusRetryHandler implements RequestHandler<Map<String, Object>, Void> {
    private static final Logger LOGGER = Logger.getLogger(DocumentStatusRetryHandler.class.getName());

    private final SqsClient sqs;
    private final ObjectMapper json = new ObjectMapper();
    private final SqsMessageReader messageReader = new SqsMessageReader(json);
    private final SarvamAiClient sarvam;

    public DocumentStatusRetryHandler() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), SqsClient.create());
    }

    DocumentStatusRetryHandler(HttpClient http, SqsClient sqs) {
        this.sqs = sqs;
        this.sarvam = new SarvamAiClient(http, json);
    }

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        try {
            var messages = messageReader.read(event);
            LOGGER.log(Level.INFO, "Received SQS batch with {0} document status message(s)", messages.size());
            for (RetryMessage message : messages) {
                process(message);
            }
        } catch (Exception e) {
            String requestId = context == null ? "unknown" : context.getAwsRequestId();
            LOGGER.log(Level.SEVERE, "Document status batch failed; requestId=" + safe(requestId), e);
            throw new IllegalStateException(StringConstant.PROCESSING_ERROR, e);
        }
        return null;
    }

    private void process(RetryMessage message) throws Exception {
        String queueUrl = RetryConfig.required(StringConstant.SQS_QUEUE_URL);
        Instant now = Instant.now();

        if (message.nextRetryAt() != null && message.nextRetryAt().isAfter(now)) {
            LOGGER.log(Level.INFO, "Retry not due; deferring documentId={0} until {1}",
                    new Object[]{safe(message.documentId()), message.nextRetryAt()});
            enqueue(queueUrl, message, now);
            return;
        }

        LOGGER.log(Level.INFO, "Checking Sarvam status for documentId={0}, retryCount={1}",
                new Object[]{safe(message.documentId()), message.retryCount()});
        String status = sarvam.fetchStatus(message.documentId());
        LOGGER.log(Level.INFO, "Sarvam status received for documentId={0}: {1}",
                new Object[]{safe(message.documentId()), safe(status)});
        String successStatus = RetryConfig.get(StringConstant.SARVAM_OCR_SUCCESS_STATUS,
                StringConstant.SARVAM_OCR_SUCCESS);
        if (successStatus.equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
            sarvam.fetchResult(message.documentId());
            LOGGER.log(Level.INFO, "Sarvam results fetched for documentId={0}", safe(message.documentId()));
            return;
        }

        int nextRetryCount = message.retryCount() + 1;
        long pollIntervalMs = Long.parseLong(RetryConfig.get(
                StringConstant.SARVAM_OCR_POLL_INTERVAL_MS, "2000"));
        if (pollIntervalMs <= 0) throw new IllegalStateException("SARVAM_OCR_POLL_INTERVAL_MS must be positive");
        Instant nextRetryAt = now.plusMillis(pollIntervalMs);
        LOGGER.log(Level.INFO, "Scheduling status retry for documentId={0}, retryCount={1}, nextRetryAt={2}",
                new Object[]{safe(message.documentId()), nextRetryCount, nextRetryAt});
        enqueue(queueUrl, new RetryMessage(message.documentId(), nextRetryCount, nextRetryAt), now);
    }

    private void enqueue(String queueUrl, RetryMessage message, Instant now) throws Exception {
        Duration remaining = Duration.between(now, message.nextRetryAt());
        long remainingSeconds = Math.max(0, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
        int delaySeconds = (int) Math.min(remainingSeconds, StringConstant.MAX_SQS_DELAY_SECONDS);
        var response = sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(serialize(message))
                .delaySeconds(delaySeconds)
                .build());
        LOGGER.log(Level.INFO, "Retry message enqueued for documentId={0}, delaySeconds={1}, sqsMessageId={2}",
                new Object[]{safe(message.documentId()), delaySeconds,
                        response == null ? "unknown" : safe(response.messageId())});
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        return value.replace('\n', '_').replace('\r', '_');
    }

    private String serialize(RetryMessage message) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(StringConstant.DOCUMENT_ID_FIELD, message.documentId());
        body.put(StringConstant.RETRY_COUNT_FIELD, message.retryCount());
        body.put(StringConstant.NEXT_RETRY_AT_FIELD,
                message.nextRetryAt() == null ? null : message.nextRetryAt().toString());
        return json.writeValueAsString(body);
    }

}
