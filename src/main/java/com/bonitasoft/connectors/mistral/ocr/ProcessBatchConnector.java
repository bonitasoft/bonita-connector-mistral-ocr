package com.bonitasoft.connectors.mistral.ocr;

import com.bonitasoft.connectors.mistral.ocr.model.ProcessBatchResult;
import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Process a multi-page document batch via Mistral OCR API.
 */
@Slf4j
public class ProcessBatchConnector extends AbstractMistralOcrConnector {

    static final String INPUT_API_KEY = "apiKey";
    static final String INPUT_BASE_URL = "baseUrl";
    static final String INPUT_MODEL = "model";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_DOCUMENT_BASE64 = "documentBase64";
    static final String INPUT_IMAGE_URLS = "imageUrls";
    static final String INPUT_MIME_TYPE = "mimeType";
    static final String INPUT_START_PAGE = "startPage";
    static final String INPUT_END_PAGE = "endPage";

    static final String OUTPUT_FULL_TEXT = "fullText";
    static final String OUTPUT_PAGES = "pages";
    static final String OUTPUT_PAGES_MAP = "pagesMap";
    static final String OUTPUT_PAGE_COUNT = "pageCount";
    static final String OUTPUT_TOTAL_WORD_COUNT = "totalWordCount";
    static final String OUTPUT_TOKENS_USED = "tokensUsed";
    static final String OUTPUT_PROCESSING_TIME_MS = "processingTimeMs";

    @Override
    protected MistralOcrConfiguration buildConfiguration() {
        return MistralOcrConfiguration.builder()
                .apiKey(readStringInput(INPUT_API_KEY))
                .baseUrl(readStringInput(INPUT_BASE_URL, "https://api.mistral.ai/v1"))
                .model(readStringInput(INPUT_MODEL, "mistral-ocr-latest"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 300000))
                .documentBase64(readStringInput(INPUT_DOCUMENT_BASE64))
                .imageUrls(readStringInput(INPUT_IMAGE_URLS))
                .mimeType(readStringInput(INPUT_MIME_TYPE, "application/pdf"))
                .startPage(readOptionalInteger(INPUT_START_PAGE))
                .endPage(readOptionalInteger(INPUT_END_PAGE))
                .build();
    }

    @Override
    protected void validateConfiguration(MistralOcrConfiguration config) {
        super.validateConfiguration(config);
        if ((config.getDocumentBase64() == null || config.getDocumentBase64().isBlank())
                && (config.getImageUrls() == null || config.getImageUrls().isBlank())) {
            throw new IllegalArgumentException("Either documentBase64 or imageUrls must be provided");
        }
    }

    @Override
    protected void initializeOutputs() {
        setOutputParameter(OUTPUT_FULL_TEXT, "");
        setOutputParameter(OUTPUT_PAGES, java.util.List.of());
        setOutputParameter(OUTPUT_PAGES_MAP, java.util.Map.of());
        setOutputParameter(OUTPUT_PAGE_COUNT, 0);
        setOutputParameter(OUTPUT_TOTAL_WORD_COUNT, 0);
        setOutputParameter(OUTPUT_TOKENS_USED, 0);
        setOutputParameter(OUTPUT_PROCESSING_TIME_MS, 0L);
    }

    @Override
    protected void doExecute() throws MistralOcrException {
        log.info("Executing Process Batch connector");
        ProcessBatchResult result = client.processBatch(configuration);
        setOutputParameter(OUTPUT_FULL_TEXT, result.fullText());
        setOutputParameter(OUTPUT_PAGES, result.pages());
        setOutputParameter(OUTPUT_PAGES_MAP, result.pagesMap());
        setOutputParameter(OUTPUT_PAGE_COUNT, result.pageCount());
        setOutputParameter(OUTPUT_TOTAL_WORD_COUNT, result.totalWordCount());
        setOutputParameter(OUTPUT_TOKENS_USED, result.tokensUsed());
        setOutputParameter(OUTPUT_PROCESSING_TIME_MS, result.processingTimeMs());
        log.info("Process Batch connector executed successfully ({} pages, {}ms)",
                result.pageCount(), result.processingTimeMs());
    }

}
