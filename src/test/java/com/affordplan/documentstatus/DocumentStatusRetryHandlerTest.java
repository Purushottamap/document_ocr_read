package com.affordplan.documentstatus;

import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentStatusRetryHandlerTest {
    private static final String QUEUE_URL = "https://sqs.example.test/document-status-retry";

    @BeforeAll
    static void configure(@TempDir Path directory) throws Exception {
        Path config = directory.resolve("application.properties");
        Files.writeString(config, """
                SQS_QUEUE_URL=%s
                SARVAM_OCR_BASE_URL=https://api.example.test
                SARVAM_OCR_API_KEY=test-key
                SARVAM_OCR_STATUS_PATH=/doc-ai/v1/job/%%s/status
                SARVAM_OCR_RESULTS_PATH=/doc-ai/v1/job/%%s/results
                SARVAM_OCR_POLL_INTERVAL_MS=2000
                """.formatted(QUEUE_URL));
        System.setProperty("APP_CONFIG_FILE", config.toString());
    }

    @Test
    void pendingDocumentIsScheduledWithNextRetry() throws Exception {
        HttpClient http = mock(HttpClient.class);
        SqsClient sqs = mock(SqsClient.class);
        stubResponse(http, "{\"status\":\"pending\"}");
        DocumentStatusRetryHandler handler = new DocumentStatusRetryHandler(http, sqs);

        handler.handleRequest(event("doc-123"), mock(Context.class));

        verify(http).send(argThat(request -> request.uri().toString()
                .equals("https://api.example.test/doc-ai/v1/job/doc-123/status")
                && "test-key".equals(request.headers().firstValue("api-subscription-key").orElse(null))),
                any(HttpResponse.BodyHandler.class));
        verify(sqs).sendMessage(argThat((SendMessageRequest request) -> {
            try {
                var message = new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.messageBody());
                return request.queueUrl().equals(QUEUE_URL)
                        && message.path("documentId").asText().equals("doc-123")
                        && message.path("retryCount").asInt() == 1
                        && Instant.parse(message.path("nextRetryAt").asText()).isAfter(Instant.now())
                        && request.delaySeconds() == 2;
            } catch (Exception e) {
                return false;
            }
        }));
    }

    @Test
    void successfulDocumentFetchesResultWithoutRetrying() throws Exception {
        HttpClient http = mock(HttpClient.class);
        SqsClient sqs = mock(SqsClient.class);
        stubResponses(http, "{\"status\":\"completed\"}", "{\"document\":\"done\"}");
        DocumentStatusRetryHandler handler = new DocumentStatusRetryHandler(http, sqs);

        handler.handleRequest(event("doc-456"), mock(Context.class));

        verify(http, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
        verify(http).send(argThat(request -> request.uri().toString()
                .equals("https://api.example.test/doc-ai/v1/job/doc-456/results")),
                any(HttpResponse.BodyHandler.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void futureRetryIsRequeuedWithoutCallingDocumentApi() throws Exception {
        HttpClient http = mock(HttpClient.class);
        SqsClient sqs = mock(SqsClient.class);
        DocumentStatusRetryHandler handler = new DocumentStatusRetryHandler(http, sqs);
        Instant nextRetry = Instant.now().plusSeconds(600);
        Map<String, Object> event = event("{\"documentId\":\"doc-789\",\"retryCount\":2,\"nextRetryAt\":\""
                + nextRetry + "\"}");

        handler.handleRequest(event, mock(Context.class));

        verifyNoInteractions(http);
        verify(sqs).sendMessage(argThat((SendMessageRequest request) -> request.messageBody().contains("doc-789")
                && request.messageBody().contains("\"retryCount\":2")
                && request.delaySeconds() > 0));
    }

    @Test
    void malformedEventFailsInvocation() {
        DocumentStatusRetryHandler handler = new DocumentStatusRetryHandler(mock(HttpClient.class), mock(SqsClient.class));
        Logger logger = Logger.getLogger(DocumentStatusRetryHandler.class.getName());
        Level originalLevel = logger.getLevel();

        IllegalStateException error;
        try {
            logger.setLevel(Level.OFF);
            error = assertThrows(IllegalStateException.class,
                    () -> handler.handleRequest(Map.of(), mock(Context.class)));
        } finally {
            logger.setLevel(originalLevel);
        }

        assertEquals(StringConstant.PROCESSING_ERROR, error.getMessage());
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private static Map<String, Object> event(String body) {
        return Map.of("Records", List.of(Map.of("body", body)));
    }

    @SuppressWarnings("unchecked")
    private static void stubResponse(HttpClient http, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private static void stubResponses(HttpClient http, String... bodies) throws Exception {
        HttpResponse<String> first = mock(HttpResponse.class);
        when(first.statusCode()).thenReturn(200);
        when(first.body()).thenReturn(bodies[0]);
        HttpResponse<String> second = mock(HttpResponse.class);
        when(second.statusCode()).thenReturn(200);
        when(second.body()).thenReturn(bodies[1]);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(first, second);
    }
}
