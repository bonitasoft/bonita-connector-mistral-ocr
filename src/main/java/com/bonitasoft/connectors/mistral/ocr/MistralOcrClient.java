package com.bonitasoft.connectors.mistral.ocr;

import com.bonitasoft.connectors.mistral.ocr.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API client facade for Mistral OCR connector.
 * All 5 operations go through POST /ocr:
 *  - extractText / processBatch: plain OCR
 *  - extractFields / classifyDocument / extractTable: OCR + document_annotation_format
 *
 * Supports both PDFs (document_url) and images (image_url), selected from mimeType.
 */
@Slf4j
public class MistralOcrClient {

    private final MistralOcrConfiguration configuration;
    private final RetryPolicy retryPolicy;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MistralOcrClient(MistralOcrConfiguration configuration) throws MistralOcrException {
        this.configuration = configuration;
        this.retryPolicy = new RetryPolicy(configuration.getMaxRetries());
        this.objectMapper = new ObjectMapper();
        try {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(configuration.getConnectTimeout()))
                    .build();
            log.debug("MistralOcrClient initialized with baseUrl={}", configuration.getBaseUrl());
        } catch (Exception e) {
            throw new MistralOcrException("Failed to initialize HTTP client", e);
        }
    }

    public ExtractTextResult extractText(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            ObjectNode body = buildOcrRequestBody(config);
            applyPageRange(body, config);

            JsonNode response = executePost(config.getBaseUrl() + "/ocr", body, config.getReadTimeout());

            List<String> pages = extractPages(response);
            Map<Integer, String> pagesMap = extractPagesMap(response);
            String fullText = pages.stream().collect(Collectors.joining("\n\n"));
            int pagesProcessed = usagePages(response);

            return new ExtractTextResult(fullText, pages, pagesMap, pages.size(), pagesProcessed);
        });
    }

    public ExtractFieldsResult extractFields(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            ObjectNode body = buildOcrRequestBody(config);
            applyPageRange(body, config);
            JsonNode userSchema = parseOrWrapSchema(config.getFieldsSchema());
            body.set("document_annotation_format", buildJsonSchemaFormat("ExtractedFields", userSchema));

            JsonNode response = executePost(config.getBaseUrl() + "/ocr", body, config.getReadTimeout());

            String annotation = response.path("document_annotation").asText("");
            List<String> pages = extractPages(response);
            Map<Integer, String> pagesMap = extractPagesMap(response);
            int pagesProcessed = usagePages(response);

            Map<String, Object> fieldsMap;
            try {
                fieldsMap = objectMapper.readValue(annotation, new TypeReference<>() {});
            } catch (Exception e) {
                fieldsMap = Map.of("raw", annotation);
            }

            // Guard against null/non-numeric confidence values from the API
            // (e.g. Mistral returns null in strictMode when no fields were found).
            Object rawConfidence = fieldsMap.get("confidence");
            double confidence = (rawConfidence instanceof Number n) ? n.doubleValue() : 1.0;

            return new ExtractFieldsResult(annotation, fieldsMap, pages, pagesMap,
                    pages.size(), fieldsMap.size(), confidence, pagesProcessed);
        });
    }

    public ClassifyDocumentResult classifyDocument(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode props = schema.putObject("properties");
            ObjectNode docType = props.putObject("document_type");
            docType.put("type", "string");
            docType.put("description",
                    "One of the following types: " + config.getDocumentTypes());
            props.putObject("confidence").put("type", "number");
            props.putObject("scores").put("type", "object").put("additionalProperties", true);
            if (config.isIncludeReasoning()) {
                props.putObject("reasoning").put("type", "string");
            }
            schema.putArray("required").add("document_type").add("confidence");
            schema.put("additionalProperties", false);

            ObjectNode body = buildOcrRequestBody(config);
            applyPageRange(body, config);
            body.set("document_annotation_format", buildJsonSchemaFormat("DocumentClassification", schema));

            JsonNode response = executePost(config.getBaseUrl() + "/ocr", body, config.getReadTimeout());

            String annotation = response.path("document_annotation").asText("{}");
            List<String> pages = extractPages(response);
            Map<Integer, String> pagesMap = extractPagesMap(response);
            int pagesProcessed = usagePages(response);

            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(annotation);
            } catch (Exception e) {
                return new ClassifyDocumentResult(annotation.trim(), 0.0, "", "{}",
                        pages, pagesMap, pages.size(), pagesProcessed);
            }

            String docTypeValue = parsed.path("document_type").asText("unknown");
            double confidence = parsed.path("confidence").asDouble(0.0);
            String reasoning = parsed.path("reasoning").asText("");
            String allScores = parsed.has("scores") ? parsed.get("scores").toString() : "{}";

            return new ClassifyDocumentResult(docTypeValue, confidence, reasoning, allScores,
                    pages, pagesMap, pages.size(), pagesProcessed);
        });
    }

    public ExtractTableResult extractTable(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode props = schema.putObject("properties");
            ObjectNode headersProp = props.putObject("headers");
            headersProp.put("type", "array");
            headersProp.putObject("items").put("type", "string");
            ObjectNode rowsProp = props.putObject("rows");
            rowsProp.put("type", "array");
            ObjectNode rowItem = rowsProp.putObject("items");
            rowItem.put("type", "object");
            rowItem.put("additionalProperties", true);
            schema.putArray("required").add("headers").add("rows");

            ObjectNode body = buildOcrRequestBody(config);
            applyPageRange(body, config);
            body.set("document_annotation_format", buildJsonSchemaFormat("TableExtraction", schema));

            JsonNode response = executePost(config.getBaseUrl() + "/ocr", body, config.getReadTimeout());

            String annotation = response.path("document_annotation").asText("{}");
            List<String> pages = extractPages(response);
            Map<Integer, String> pagesMap = extractPagesMap(response);
            int pagesProcessed = usagePages(response);

            List<Map<String, String>> tableDataList;
            String detectedHeaders;
            try {
                JsonNode parsed = objectMapper.readTree(annotation);
                JsonNode rows = parsed.has("rows") ? parsed.get("rows") : parsed;
                tableDataList = rows.isArray()
                        ? objectMapper.convertValue(rows, new TypeReference<>() {})
                        : List.of();
                detectedHeaders = parsed.has("headers") ? parsed.get("headers").toString() : "[]";
            } catch (Exception e) {
                tableDataList = List.of();
                detectedHeaders = "[]";
            }

            int rowCount = tableDataList.size();
            int columnCount = tableDataList.isEmpty() ? 0 : tableDataList.get(0).size();

            return new ExtractTableResult(annotation, tableDataList, pages, pagesMap,
                    pages.size(), rowCount, columnCount, detectedHeaders, pagesProcessed);
        });
    }

    public ProcessBatchResult processBatch(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            long startTime = System.currentTimeMillis();
            ObjectNode body = buildOcrRequestBody(config);
            applyPageRange(body, config);

            JsonNode response = executePost(config.getBaseUrl() + "/ocr", body, config.getReadTimeout());

            List<String> pages = extractPages(response);
            Map<Integer, String> pagesMap = extractPagesMap(response);
            String fullText = pages.stream().collect(Collectors.joining("\n\n"));
            int totalWordCount = fullText.isBlank() ? 0 : fullText.split("\\s+").length;
            int pagesProcessed = usagePages(response);
            long processingTimeMs = System.currentTimeMillis() - startTime;

            return new ProcessBatchResult(fullText, pages, pagesMap, pages.size(),
                    totalWordCount, pagesProcessed, processingTimeMs);
        });
    }

    /**
     * Apply the optional page-range filter to the OCR request body.
     * Users pass 1-indexed page numbers (natural language: "page 1, 2, 3..."),
     * but Mistral's API is 0-indexed. Converts and filters invalid values.
     *
     * Both inputs must be set together to take effect. If only one is provided,
     * the entire document is processed — but a warning is logged so the user
     * sees that their partial input was ignored. When both are null (default),
     * the whole document is processed silently — backwards-compatible with
     * older .proc files that do not set these inputs.
     */
    private void applyPageRange(ObjectNode body, MistralOcrConfiguration config) throws MistralOcrException {
        Integer start = config.getStartPage();
        Integer end = config.getEndPage();
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null) {
            throw new MistralOcrException(
                    "startPage and endPage must be set together (got startPage=" + start
                            + ", endPage=" + end + "). Leave both blank to process the entire document.");
        }
        if (start < 1 || end < start) {
            throw new MistralOcrException(
                    "Invalid page range startPage=" + start + ", endPage=" + end
                            + ". Pages are 1-indexed and endPage must be >= startPage.");
        }
        ArrayNode pagesArray = body.putArray("pages");
        for (int i = start; i <= end; i++) {
            pagesArray.add(i - 1);
        }
    }

    // === Private helpers ===

    private ObjectNode buildOcrRequestBody(MistralOcrConfiguration config) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModel());

        ObjectNode document = objectMapper.createObjectNode();
        String mimeType = config.getMimeType() != null ? config.getMimeType() : "application/pdf";
        boolean isImage = mimeType.startsWith("image/");
        String typeField = isImage ? "image_url" : "document_url";

        if (config.getDocumentBase64() != null && !config.getDocumentBase64().isBlank()) {
            document.put("type", typeField);
            document.put(typeField, "data:" + mimeType + ";base64," + config.getDocumentBase64());
        } else if (config.getImageUrl() != null && !config.getImageUrl().isBlank()) {
            document.put("type", typeField);
            document.put(typeField, config.getImageUrl());
        }
        body.set("document", document);
        return body;
    }

    private ObjectNode buildJsonSchemaFormat(String name, JsonNode schema) {
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        ObjectNode jsonSchema = format.putObject("json_schema");
        jsonSchema.put("name", name);
        jsonSchema.set("schema", schema);
        jsonSchema.put("strict", false);
        return format;
    }

    private JsonNode parseOrWrapSchema(String raw) {
        if (raw == null || raw.isBlank()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("type", "object");
            return empty;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("fieldsSchema is not valid JSON; sending a permissive fallback schema. Raw value: {}", raw);
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("type", "object");
            fallback.put("additionalProperties", true);
            return fallback;
        }
    }

    private JsonNode executePost(String url, ObjectNode body, int readTimeout) throws MistralOcrException {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("POST {} with body length={}", url, jsonBody.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + configuration.getApiKey())
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMillis(readTimeout))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                boolean retryable = RetryPolicy.isRetryableStatusCode(response.statusCode());
                String errorMsg = "Mistral API error (HTTP " + response.statusCode() + "): " + response.body();
                throw new MistralOcrException(errorMsg, response.statusCode(), retryable);
            }

            return objectMapper.readTree(response.body());
        } catch (MistralOcrException e) {
            throw e;
        } catch (IOException e) {
            throw new MistralOcrException("Network error calling Mistral API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MistralOcrException("Request interrupted", e);
        }
    }

    private List<String> extractPages(JsonNode response) {
        List<String> pages = new ArrayList<>();
        JsonNode pagesNode = response.path("pages");
        if (pagesNode.isArray()) {
            for (JsonNode page : pagesNode) {
                String markdown = page.path("markdown").asText("");
                pages.add(markdown);
            }
        }
        return pages;
    }

    /**
     * Build a 1-indexed page-number -> markdown map from the OCR response.
     * Uses the "index" field of each page (which Mistral emits 0-indexed) and
     * shifts it by +1 so downstream .proc scripts can iterate in human terms
     * (page 1, 2, 3 ...). Preserves insertion order.
     */
    private java.util.Map<Integer, String> extractPagesMap(JsonNode response) {
        java.util.Map<Integer, String> map = new java.util.LinkedHashMap<>();
        JsonNode pagesNode = response.path("pages");
        if (pagesNode.isArray()) {
            int fallback = 0;
            for (JsonNode page : pagesNode) {
                int idx0 = page.path("index").asInt(-1);
                int oneIndexed = (idx0 >= 0) ? idx0 + 1 : ++fallback;
                map.put(oneIndexed, page.path("markdown").asText(""));
            }
        }
        return map;
    }

    /** The OCR endpoint reports usage under "usage_info.pages_processed". */
    private int usagePages(JsonNode response) {
        JsonNode usageInfo = response.path("usage_info");
        if (!usageInfo.isMissingNode()) {
            return usageInfo.path("pages_processed").asInt(0);
        }
        return response.path("usage").path("total_tokens").asInt(0);
    }
}
