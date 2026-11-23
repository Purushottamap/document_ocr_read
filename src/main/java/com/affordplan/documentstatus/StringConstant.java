package com.affordplan.documentstatus;

/** Shared string values and configuration keys for the status retry Lambda. */
final class StringConstant {
    static final String CONFIG_FILE_PROPERTY = "APP_CONFIG_FILE";
    static final String DEFAULT_CONFIG_PATH =
            "/home/ubuntu/Setups/chef/current/conf/applicationfileUploader.properties";

    static final String SQS_QUEUE_URL = "SQS_QUEUE_URL";
    static final String DOCUMENT_STATUS_URL_TEMPLATE = "DOCUMENT_STATUS_URL_TEMPLATE";
    static final String DOCUMENT_RESULT_URL_TEMPLATE = "DOCUMENT_RESULT_URL_TEMPLATE";
    static final String DOCUMENT_STATUS_FIELD = "DOCUMENT_STATUS_FIELD";
    static final String DOCUMENT_SUCCESS_STATUS = "DOCUMENT_SUCCESS_STATUS";
    static final String DOCUMENT_API_TOKEN = "DOCUMENT_API_TOKEN";
    static final String API_TOKEN_HEADER = "API_TOKEN_HEADER";
    static final String API_TIMEOUT_SECONDS = "API_TIMEOUT_SECONDS";
    static final String SARVAM_OCR_BASE_URL = "SARVAM_OCR_BASE_URL";
    static final String SARVAM_OCR_API_KEY = "SARVAM_OCR_API_KEY";
    static final String SARVAM_OCR_STATUS_PATH = "SARVAM_OCR_STATUS_PATH";
    static final String SARVAM_OCR_RESULTS_PATH = "SARVAM_OCR_RESULTS_PATH";
    static final String SARVAM_OCR_POLL_INTERVAL_MS = "SARVAM_OCR_POLL_INTERVAL_MS";
    static final String SARVAM_OCR_SUCCESS_STATUS = "SARVAM_OCR_SUCCESS_STATUS";
    static final String SARVAM_OCR_SUCCESS = "completed";
    static final String SARVAM_API_KEY_HEADER = "api-subscription-key";

    static final String DEFAULT_DOCUMENT_STATUS_FIELD = "status";
    static final String DEFAULT_DOCUMENT_SUCCESS_STATUS = "SUCCESS";
    static final String DEFAULT_API_TOKEN_HEADER = "Authorization";
    static final String DEFAULT_API_TIMEOUT_SECONDS = "20";

    static final String SQS_RECORDS_FIELD = "Records";
    static final String SQS_BODY_FIELD = "body";
    static final String DOCUMENT_ID_FIELD = "documentId";
    static final String RETRY_COUNT_FIELD = "retryCount";
    static final String NEXT_RETRY_AT_FIELD = "nextRetryAt";
    static final String ID_URL_PLACEHOLDER = "{id}";
    static final String ACCEPT_HEADER = "Accept";
    static final String JSON_CONTENT_TYPE = "application/json";
    static final String EMPTY = "";
    static final String JSON_OBJECT_START = "{";
    static final String UTILITY_CLASS_MESSAGE = "Utility class";

    static final String INVALID_SQS_EVENT = "Expected an SQS event with Records";
    static final String INVALID_SQS_RECORD = "SQS record is missing its body";
    static final String PROCESSING_ERROR = "Unable to process document status message";
    static final String MISSING_DOCUMENT_ID = "Retry message is missing documentId";
    static final String EMPTY_MESSAGE_BODY = "SQS message body must contain a document ID";
    static final String MISSING_STATUS = "Document status API response is missing the configured status field";
    static final String HTTP_STATUS_ERROR_PREFIX = "Document status API returned HTTP ";
    static final String MISSING_CONFIG_PREFIX = "Missing required configuration: ";
    static final String CONFIG_LOAD_ERROR_PREFIX = "Unable to load properties from ";

    static final long MAX_SQS_DELAY_SECONDS = 900;


    private StringConstant() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }
}
