package com.bonitasoft.connectors.mistral.ocr.model;

import java.util.List;
import java.util.Map;

public record ExtractTableResult(
        String tableData,
        List<Map<String, String>> tableDataList,
        int rowCount,
        int columnCount,
        String detectedHeaders,
        int tokensUsed
) {}
