package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.List;

public record ProcessBatchResult(
        String fullText,
        List<String> pages,
        int pageCount,
        int totalWordCount,
        int tokensUsed,
        long processingTimeMs
) {}
