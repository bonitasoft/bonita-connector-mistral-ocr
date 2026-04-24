package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.List;
import java.util.Map;

public record ExtractFieldsResult(
        String extractedFields,
        Map<String, Object> extractedFieldsMap,
        List<String> pages,
        Map<Integer, String> pagesMap,
        int pageCount,
        int fieldCount,
        double confidence,
        int tokensUsed
) {}
