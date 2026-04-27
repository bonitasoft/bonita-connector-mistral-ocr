package com.bonitasoft.connectors.mistral.ocr;

import com.bonitasoft.connectors.mistral.ocr.model.ClassifyDocumentResult;
import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Classify a document into one of the provided types using Mistral Vision.
 */
@Slf4j
public class ClassifyDocumentConnector extends AbstractMistralOcrConnector {

    static final String INPUT_API_KEY = "apiKey";
    static final String INPUT_BASE_URL = "baseUrl";
    static final String INPUT_MODEL = "model";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_DOCUMENT_BASE64 = "documentBase64";
    static final String INPUT_IMAGE_URL = "imageUrl";
    static final String INPUT_MIME_TYPE = "mimeType";
    static final String INPUT_DOCUMENT_TYPES = "documentTypes";
    static final String INPUT_INCLUDE_REASONING = "includeReasoning";
    static final String INPUT_START_PAGE = "startPage";
    static final String INPUT_END_PAGE = "endPage";

    static final String OUTPUT_DOCUMENT_TYPE = "documentType";
    static final String OUTPUT_CONFIDENCE = "confidence";
    static final String OUTPUT_REASONING = "reasoning";
    static final String OUTPUT_ALL_SCORES = "allScores";
    static final String OUTPUT_PAGES = "pages";
    static final String OUTPUT_PAGES_MAP = "pagesMap";
    static final String OUTPUT_PAGE_COUNT = "pageCount";
    static final String OUTPUT_PAGES_PROCESSED = "pagesProcessed";

    @Override
    protected MistralOcrConfiguration buildConfiguration() {
        return MistralOcrConfiguration.builder()
                .apiKey(readStringInput(INPUT_API_KEY))
                .baseUrl(readStringInput(INPUT_BASE_URL, "https://api.mistral.ai/v1"))
                .model(readStringInput(INPUT_MODEL, "pixtral-large-latest"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 120000))
                .documentBase64(readStringInput(INPUT_DOCUMENT_BASE64))
                .imageUrl(readStringInput(INPUT_IMAGE_URL))
                .mimeType(readStringInput(INPUT_MIME_TYPE, "application/pdf"))
                .documentTypes(readStringInput(INPUT_DOCUMENT_TYPES))
                .includeReasoning(readBooleanInput(INPUT_INCLUDE_REASONING, false))
                .startPage(readOptionalInteger(INPUT_START_PAGE))
                .endPage(readOptionalInteger(INPUT_END_PAGE))
                .build();
    }

    @Override
    protected void validateConfiguration(MistralOcrConfiguration config) {
        super.validateConfiguration(config);
        if ((config.getDocumentBase64() == null || config.getDocumentBase64().isBlank())
                && (config.getImageUrl() == null || config.getImageUrl().isBlank())) {
            throw new IllegalArgumentException("Either documentBase64 or imageUrl must be provided");
        }
        if (config.getDocumentTypes() == null || config.getDocumentTypes().isBlank()) {
            throw new IllegalArgumentException("documentTypes is mandatory");
        }
    }

    @Override
    protected void initializeOutputs() {
        setOutputParameter(OUTPUT_DOCUMENT_TYPE, "");
        setOutputParameter(OUTPUT_CONFIDENCE, 0.0);
        setOutputParameter(OUTPUT_REASONING, "");
        setOutputParameter(OUTPUT_ALL_SCORES, "{}");
        setOutputParameter(OUTPUT_PAGES, java.util.List.of());
        setOutputParameter(OUTPUT_PAGES_MAP, java.util.Map.of());
        setOutputParameter(OUTPUT_PAGE_COUNT, 0);
        setOutputParameter(OUTPUT_PAGES_PROCESSED, 0);
    }

    @Override
    protected void doExecute() throws MistralOcrException {
        log.info("Executing Classify Document connector");
        ClassifyDocumentResult result = client.classifyDocument(configuration);
        setOutputParameter(OUTPUT_DOCUMENT_TYPE, result.documentType());
        setOutputParameter(OUTPUT_CONFIDENCE, result.confidence());
        setOutputParameter(OUTPUT_REASONING, result.reasoning());
        setOutputParameter(OUTPUT_ALL_SCORES, result.allScores());
        setOutputParameter(OUTPUT_PAGES, result.pages());
        setOutputParameter(OUTPUT_PAGES_MAP, result.pagesMap());
        setOutputParameter(OUTPUT_PAGE_COUNT, result.pageCount());
        setOutputParameter(OUTPUT_PAGES_PROCESSED, result.pagesProcessed());
        log.info("Classify Document connector executed successfully: type={}", result.documentType());
    }
}
