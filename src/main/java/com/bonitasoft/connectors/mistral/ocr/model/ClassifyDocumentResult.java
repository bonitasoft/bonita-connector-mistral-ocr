package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.List;
import java.util.Map;

public record ClassifyDocumentResult(
        String documentType,
        double confidence,
        String reasoning,
        String allScores,
        List<String> pages,
        Map<Integer, String> pagesMap,
        int pageCount,
        int tokensUsed
) {}
