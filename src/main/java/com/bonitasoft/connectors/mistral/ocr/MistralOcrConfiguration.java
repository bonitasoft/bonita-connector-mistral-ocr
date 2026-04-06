package com.bonitasoft.connectors.mistral.ocr;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for all Mistral OCR connector operations.
 */
@Data
@Builder
public class MistralOcrConfiguration {

    // === Connection / Auth parameters ===
    private String apiKey;
    @Builder.Default
    private String baseUrl = "https://api.mistral.ai/v1";
    @Builder.Default
    private String model = "mistral-ocr-latest";
    @Builder.Default
    private int connectTimeout = 30000;
    @Builder.Default
    private int readTimeout = 120000;

    // === Document input (shared across operations) ===
    private String documentBase64;
    private String imageUrl;
    @Builder.Default
    private String mimeType = "application/pdf";

    // === Extract Text parameters ===
    @Builder.Default
    private boolean includePageSegmentation = true;
    private String language;

    // === Extract Fields parameters ===
    private String fieldsSchema;
    private String extractionPrompt;
    @Builder.Default
    private boolean strictMode = false;

    // === Classify Document parameters ===
    private String documentTypes;
    @Builder.Default
    private boolean includeReasoning = false;

    // === Extract Table parameters ===
    private String columnHeaders;
    private String tableHint;
    @Builder.Default
    private int pageNumber = 1;

    // === Process Batch parameters ===
    private String imageUrls;
    private Integer startPage;
    private Integer endPage;

    // === Advanced ===
    @Builder.Default
    private int maxRetries = 3;
}
