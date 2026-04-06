package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.List;

public record ExtractTextResult(
        String extractedText,
        List<String> pages,
        int pageCount,
        int tokensUsed
) {}
