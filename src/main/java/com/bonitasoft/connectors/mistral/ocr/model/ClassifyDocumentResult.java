package com.bonitasoft.connectors.mistral.ocr.model;

public record ClassifyDocumentResult(
        String documentType,
        double confidence,
        String reasoning,
        String allScores,
        int tokensUsed
) {}
