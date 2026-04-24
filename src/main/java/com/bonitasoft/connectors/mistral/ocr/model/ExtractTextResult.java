package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.List;
import java.util.Map;

public record ExtractTextResult(
        String extractedText,
        List<String> pages,
        Map<Integer, String> pagesMap,
        int pageCount,
        int tokensUsed
) {}
