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
import java.util.*;
import java.util.stream.Collectors;

/**
 * API client facade for Mistral OCR connector.
 * Uses java.net.http.HttpClient and Jackson for JSON processing.
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

    /**
     * Extract text from a document via POST /ocr.
     */
    public ExtractTextResult extractText(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            ObjectNode body = buildOcrRequestBody(config);
            if (config.getLanguage() != null && !config.getLanguage().isBlank()) {
                body.put("language", config.getLanguage());
            }
            body.put("include_page_segmentation", config.isIncludePageSegmentation());

            JsonNode response = executePost(config.getBaseUrl() + "/ocr", body, config.getReadTimeout());

            List<String> pages = extractPages(response);
            String fullText = pages.stream().collect(Collectors.joining("\n\n"));
            int tokensUsed = response.path("usage").path("total_tokens").asInt(0);

            return new ExtractTextResult(fullText, pages, pages.size(), tokensUsed);
        });
    }

    /**
     * Extract structured fields via POST /chat/completions (vision + JSON mode).
     */
    public ExtractFieldsResult extractFields(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            String prompt = buildFieldExtractionPrompt(config);
            ObjectNode body = buildChatCompletionBody(config, prompt, true);

            JsonNode response = executePost(config.getBaseUrl() + "/chat/completions", body, config.getReadTimeout());

            String content = extractChatContent(response);
            int tokensUsed = response.path("usage").path("total_tokens").asInt(0);

            Map<String, Object> fieldsMap;
            try {
                fieldsMap = objectMapper.readValue(content, new TypeReference<>() {});
            } catch (Exception e) {
                fieldsMap = Map.of("raw", content);
            }

            double confidence = fieldsMap.containsKey("confidence")
                    ? ((Number) fieldsMap.get("confidence")).doubleValue()
                    : 1.0;

            return new ExtractFieldsResult(content, fieldsMap, fieldsMap.size(), confidence, tokensUsed);
        });
    }

    /**
     * Classify a document via POST /chat/completions (vision + classification prompt).
     */
    public ClassifyDocumentResult classifyDocument(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            String prompt = buildClassificationPrompt(config);
            ObjectNode body = buildChatCompletionBody(config, prompt, true);

            JsonNode response = executePost(config.getBaseUrl() + "/chat/completions", body, config.getReadTimeout());

            String content = extractChatContent(response);
            int tokensUsed = response.path("usage").path("total_tokens").asInt(0);

            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(content);
            } catch (Exception e) {
                return new ClassifyDocumentResult(content.trim(), 0.0, "", "{}", tokensUsed);
            }

            String docType = parsed.path("document_type").asText(parsed.path("type").asText("unknown"));
            double confidence = parsed.path("confidence").asDouble(0.0);
            String reasoning = parsed.path("reasoning").asText("");
            String allScores = parsed.has("scores") ? parsed.get("scores").toString() : "{}";

            return new ClassifyDocumentResult(docType, confidence, reasoning, allScores, tokensUsed);
        });
    }

    /**
     * Extract table data via POST /chat/completions (vision + table extraction prompt).
     */
    public ExtractTableResult extractTable(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            String prompt = buildTableExtractionPrompt(config);
            ObjectNode body = buildChatCompletionBody(config, prompt, true);

            JsonNode response = executePost(config.getBaseUrl() + "/chat/completions", body, config.getReadTimeout());

            String content = extractChatContent(response);
            int tokensUsed = response.path("usage").path("total_tokens").asInt(0);

            List<Map<String, String>> tableDataList;
            String detectedHeaders;
            try {
                JsonNode parsed = objectMapper.readTree(content);
                JsonNode rows = parsed.has("rows") ? parsed.get("rows") : parsed;
                if (rows.isArray()) {
                    tableDataList = objectMapper.convertValue(rows, new TypeReference<>() {});
                } else {
                    tableDataList = List.of();
                }
                detectedHeaders = parsed.has("headers") ? parsed.get("headers").toString() : "[]";
            } catch (Exception e) {
                tableDataList = List.of();
                detectedHeaders = "[]";
            }

            int rowCount = tableDataList.size();
            int columnCount = tableDataList.isEmpty() ? 0 : tableDataList.get(0).size();

            return new ExtractTableResult(content, tableDataList, rowCount, columnCount, detectedHeaders, tokensUsed);
        });
    }

    /**
     * Process a batch of pages via POST /ocr (multi-page).
     */
    public ProcessBatchResult processBatch(MistralOcrConfiguration config) throws MistralOcrException {
        return retryPolicy.execute(() -> {
            long startTime = System.currentTimeMillis();
            ObjectNode body = buildOcrRequestBody(config);
            body.put("include_page_segmentation", true);

            if (config.getStartPage() != null) {
                body.put("start_page", config.getStartPage());
            }
            if (config.getEndPage() != null) {
                body.put("end_page", config.getEndPage());
            }

            JsonNode response = executePost(config.getBaseUrl() + "/ocr", body, config.getReadTimeout());

            List<String> pages = extractPages(response);
            String fullText = pages.stream().collect(Collectors.joining("\n\n"));
            int totalWordCount = fullText.isBlank() ? 0 : fullText.split("\\s+").length;
            int tokensUsed = response.path("usage").path("total_tokens").asInt(0);
            long processingTimeMs = System.currentTimeMillis() - startTime;

            return new ProcessBatchResult(fullText, pages, pages.size(), totalWordCount, tokensUsed, processingTimeMs);
        });
    }

    // === Private helpers ===

    private ObjectNode buildOcrRequestBody(MistralOcrConfiguration config) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModel());

        ObjectNode document = objectMapper.createObjectNode();
        if (config.getDocumentBase64() != null && !config.getDocumentBase64().isBlank()) {
            document.put("type", "base64");
            document.put("data", config.getDocumentBase64());
            document.put("mime_type", config.getMimeType());
        } else if (config.getImageUrl() != null && !config.getImageUrl().isBlank()) {
            document.put("type", "url");
            document.put("url", config.getImageUrl());
        }
        body.set("document", document);
        return body;
    }

    private ObjectNode buildChatCompletionBody(MistralOcrConfiguration config, String prompt, boolean jsonMode) {
        ObjectNode body = objectMapper.createObjectNode();
        // Use pixtral-large-latest for vision tasks by default
        String chatModel = config.getModel().contains("ocr") ? "pixtral-large-latest" : config.getModel();
        body.put("model", chatModel);

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");

        ArrayNode contentArray = userMessage.putArray("content");

        // Add image content
        ObjectNode imageContent = contentArray.addObject();
        imageContent.put("type", "image_url");
        ObjectNode imageUrl = imageContent.putObject("image_url");
        if (config.getDocumentBase64() != null && !config.getDocumentBase64().isBlank()) {
            String dataUri = "data:" + config.getMimeType() + ";base64," + config.getDocumentBase64();
            imageUrl.put("url", dataUri);
        } else if (config.getImageUrl() != null && !config.getImageUrl().isBlank()) {
            imageUrl.put("url", config.getImageUrl());
        }

        // Add text prompt
        ObjectNode textContent = contentArray.addObject();
        textContent.put("type", "text");
        textContent.put("text", prompt);

        if (jsonMode) {
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_object");
        }

        return body;
    }

    private String buildFieldExtractionPrompt(MistralOcrConfiguration config) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract the following fields from this document and return them as a JSON object.\n\n");
        sb.append("Fields schema:\n").append(config.getFieldsSchema()).append("\n\n");
        if (config.getExtractionPrompt() != null && !config.getExtractionPrompt().isBlank()) {
            sb.append("Additional instructions:\n").append(config.getExtractionPrompt()).append("\n\n");
        }
        if (config.isStrictMode()) {
            sb.append("IMPORTANT: Only return fields that are explicitly present in the document. ");
            sb.append("Set missing fields to null. Do not infer or guess values.\n\n");
        }
        sb.append("Return ONLY valid JSON, no markdown formatting.");
        return sb.toString();
    }

    private String buildClassificationPrompt(MistralOcrConfiguration config) {
        StringBuilder sb = new StringBuilder();
        sb.append("Classify this document into one of the following types:\n");
        sb.append(config.getDocumentTypes()).append("\n\n");
        sb.append("Return a JSON object with the following fields:\n");
        sb.append("- \"document_type\": the classified type (must be one of the types listed above)\n");
        sb.append("- \"confidence\": a number between 0 and 1 indicating confidence\n");
        sb.append("- \"scores\": an object mapping each document type to its score\n");
        if (config.isIncludeReasoning()) {
            sb.append("- \"reasoning\": a brief explanation of why this classification was chosen\n");
        }
        sb.append("\nReturn ONLY valid JSON, no markdown formatting.");
        return sb.toString();
    }

    private String buildTableExtractionPrompt(MistralOcrConfiguration config) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extract table data from this document");
        if (config.getPageNumber() > 1) {
            sb.append(" (focus on page ").append(config.getPageNumber()).append(")");
        }
        sb.append(".\n\n");
        if (config.getColumnHeaders() != null && !config.getColumnHeaders().isBlank()) {
            sb.append("Expected column headers: ").append(config.getColumnHeaders()).append("\n\n");
        }
        if (config.getTableHint() != null && !config.getTableHint().isBlank()) {
            sb.append("Hint: ").append(config.getTableHint()).append("\n\n");
        }
        sb.append("Return a JSON object with:\n");
        sb.append("- \"headers\": array of column header strings\n");
        sb.append("- \"rows\": array of objects where each key is a header and value is the cell content\n");
        sb.append("\nReturn ONLY valid JSON, no markdown formatting.");
        return sb.toString();
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

    private String extractChatContent(JsonNode response) throws MistralOcrException {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new MistralOcrException("No choices in Mistral API response");
        }
        return choices.get(0).path("message").path("content").asText("");
    }
}
