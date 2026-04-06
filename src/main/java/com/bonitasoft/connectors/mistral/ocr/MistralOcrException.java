package com.bonitasoft.connectors.mistral.ocr;

/**
 * Typed exception for Mistral OCR connector operations.
 */
public class MistralOcrException extends Exception {

    private final int statusCode;
    private final boolean retryable;

    public MistralOcrException(String message) {
        super(message);
        this.statusCode = -1;
        this.retryable = false;
    }

    public MistralOcrException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.retryable = false;
    }

    public MistralOcrException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public MistralOcrException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
