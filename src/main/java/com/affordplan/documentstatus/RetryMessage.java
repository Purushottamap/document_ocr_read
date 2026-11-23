package com.affordplan.documentstatus;

import java.time.Instant;

record RetryMessage(String documentId, int retryCount, Instant nextRetryAt) {}
