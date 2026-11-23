package com.affordplan.documentstatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Owns HTTP communication with the Sarvam AI job APIs. */
final class SarvamAiClient {
    private final HttpClient http;
    private final ObjectMapper json;

    SarvamAiClient(HttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    String fetchStatus(String documentId) throws Exception {
        String body = get(url(StringConstant.SARVAM_OCR_STATUS_PATH, documentId));
        JsonNode status = json.readTree(body).path(RetryConfig.get(StringConstant.DOCUMENT_STATUS_FIELD,
                StringConstant.DEFAULT_DOCUMENT_STATUS_FIELD));
        if (!status.isValueNode() || status.asText().isBlank()) {
            throw new IllegalStateException(StringConstant.MISSING_STATUS);
        }
        return status.asText();
    }

    String fetchResult(String documentId) throws Exception {
        return get(url(StringConstant.SARVAM_OCR_RESULTS_PATH, documentId));
    }

    private String url(String pathKey, String documentId) {
        String baseUrl = RetryConfig.required(StringConstant.SARVAM_OCR_BASE_URL).replaceAll("/+$", "");
        String path = RetryConfig.required(pathKey);
        String encodedId = URLEncoder.encode(documentId, StandardCharsets.UTF_8);
        if (path.contains("%s")) path = path.formatted(encodedId);
        else path = path.replace(StringConstant.ID_URL_PLACEHOLDER, encodedId);
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private String get(String url) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Long.parseLong(RetryConfig.get(
                        StringConstant.API_TIMEOUT_SECONDS, StringConstant.DEFAULT_API_TIMEOUT_SECONDS))))
                .header(StringConstant.ACCEPT_HEADER, StringConstant.JSON_CONTENT_TYPE)
                .GET();
        String token = RetryConfig.get(StringConstant.SARVAM_OCR_API_KEY, StringConstant.EMPTY);
        if (!token.isBlank()) request.header(StringConstant.SARVAM_API_KEY_HEADER, token);
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(StringConstant.HTTP_STATUS_ERROR_PREFIX + response.statusCode());
        }
        return response.body();
    }
}
