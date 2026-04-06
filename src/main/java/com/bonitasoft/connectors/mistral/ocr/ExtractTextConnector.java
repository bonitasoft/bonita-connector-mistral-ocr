package com.bonitasoft.connectors.mistral.ocr;

import com.bonitasoft.connectors.mistral.ocr.model.ExtractTextResult;
import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Extract all text from a PDF or image document using Mistral OCR API.
 */
@Slf4j
public class ExtractTextConnector extends AbstractMistralOcrConnector {

    static final String INPUT_API_KEY = "apiKey";
    static final String INPUT_BASE_URL = "baseUrl";
    static final String INPUT_MODEL = "model";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_DOCUMENT_BASE64 = "documentBase64";
    static final String INPUT_IMAGE_URL = "imageUrl";
    static final String INPUT_MIME_TYPE = "mimeType";
    static final String INPUT_INCLUDE_PAGE_SEGMENTATION = "includePageSegmentation";
    static final String INPUT_LANGUAGE = "language";

    static final String OUTPUT_EXTRACTED_TEXT = "extractedText";
    static final String OUTPUT_PAGES = "pages";
    static final String OUTPUT_PAGE_COUNT = "pageCount";
    static final String OUTPUT_TOKENS_USED = "tokensUsed";

    @Override
    protected MistralOcrConfiguration buildConfiguration() {
        return MistralOcrConfiguration.builder()
                .apiKey(readStringInput(INPUT_API_KEY))
                .baseUrl(readStringInput(INPUT_BASE_URL, "https://api.mistral.ai/v1"))
                .model(readStringInput(INPUT_MODEL, "mistral-ocr-latest"))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 120000))
                .documentBase64(readStringInput(INPUT_DOCUMENT_BASE64))
                .imageUrl(readStringInput(INPUT_IMAGE_URL))
                .mimeType(readStringInput(INPUT_MIME_TYPE, "application/pdf"))
                .includePageSegmentation(readBooleanInput(INPUT_INCLUDE_PAGE_SEGMENTATION, true))
                .language(readStringInput(INPUT_LANGUAGE))
                .build();
    }

    @Override
    protected void validateConfiguration(MistralOcrConfiguration config) {
        super.validateConfiguration(config);
        if ((config.getDocumentBase64() == null || config.getDocumentBase64().isBlank())
                && (config.getImageUrl() == null || config.getImageUrl().isBlank())) {
            throw new IllegalArgumentException("Either documentBase64 or imageUrl must be provided");
        }
    }

    @Override
    protected void doExecute() throws MistralOcrException {
        log.info("Executing Extract Text connector");
        ExtractTextResult result = client.extractText(configuration);
        setOutputParameter(OUTPUT_EXTRACTED_TEXT, result.extractedText());
        setOutputParameter(OUTPUT_PAGES, result.pages());
        setOutputParameter(OUTPUT_PAGE_COUNT, result.pageCount());
        setOutputParameter(OUTPUT_TOKENS_USED, result.tokensUsed());
        log.info("Extract Text connector executed successfully ({} pages)", result.pageCount());
    }
}
