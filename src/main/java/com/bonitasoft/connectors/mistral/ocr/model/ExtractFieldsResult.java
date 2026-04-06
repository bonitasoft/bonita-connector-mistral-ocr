package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.Map;

public record ExtractFieldsResult(
        String extractedFields,
        Map<String, Object> extractedFieldsMap,
        int fieldCount,
        double confidence,
        int tokensUsed
) {}
