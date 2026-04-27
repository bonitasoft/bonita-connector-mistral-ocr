package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.List;
import java.util.Map;

public record ProcessBatchResult(
        String fullText,
        List<String> pages,
        Map<Integer, String> pagesMap,
        int pageCount,
        int totalWordCount,
        int pagesProcessed,
        long processingTimeMs
) {}
