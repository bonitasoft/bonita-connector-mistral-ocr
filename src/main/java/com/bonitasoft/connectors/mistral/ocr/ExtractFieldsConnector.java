package com.bonitasoft.connectors.mistral.ocr;

import com.bonitasoft.connectors.mistral.ocr.model.ExtractFieldsResult;
import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Extract structured fields from a document using Mistral Vision + JSON mode.
 */
@Slf4j
public class ExtractFieldsConnector extends AbstractMistralOcrConnector {

    static final String INPUT_API_KEY = "apiKey";
    static final String INPUT_BASE_URL = "baseUrl";
    static final String INPUT_MODEL = "model";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_DOCUMENT_BASE64 = "documentBase64";
    static final String INPUT_IMAGE_URL = "imageUrl";
    static final String INPUT_MIME_TYPE = "mimeType";
    static final String INPUT_FIELDS_SCHEMA = "fieldsSchema";
    static final String INPUT_EXTRACTION_PROMPT = "extractionPrompt";
    static final String INPUT_STRICT_MODE = "strictMode";
    static final String INPUT_START_PAGE = "startPage";
    static final String INPUT_END_PAGE = "endPage";

    static final String OUTPUT_EXTRACTED_FIELDS = "extractedFields";
    static final String OUTPUT_EXTRACTED_FIELDS_MAP = "extractedFieldsMap";
    static final String OUTPUT_PAGES = "pages";
    static final String OUTPUT_PAGES_MAP = "pagesMap";
    static final String OUTPUT_PAGE_COUNT = "pageCount";
    static final String OUTPUT_FIELD_COUNT = "fieldCount";
    static final String OUTPUT_CONFIDENCE = "confidence";
    static final String OUTPUT_TOKENS_USED = "tokensUsed";

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
                .fieldsSchema(readStringInput(INPUT_FIELDS_SCHEMA))
                .extractionPrompt(readStringInput(INPUT_EXTRACTION_PROMPT))
                .strictMode(readBooleanInput(INPUT_STRICT_MODE, false))
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
        if (config.getFieldsSchema() == null || config.getFieldsSchema().isBlank()) {
            throw new IllegalArgumentException("fieldsSchema is mandatory");
        }
    }

    @Override
    protected void initializeOutputs() {
        setOutputParameter(OUTPUT_EXTRACTED_FIELDS, "");
        setOutputParameter(OUTPUT_EXTRACTED_FIELDS_MAP, java.util.Map.of());
        setOutputParameter(OUTPUT_PAGES, java.util.List.of());
        setOutputParameter(OUTPUT_PAGES_MAP, java.util.Map.of());
        setOutputParameter(OUTPUT_PAGE_COUNT, 0);
        setOutputParameter(OUTPUT_FIELD_COUNT, 0);
        setOutputParameter(OUTPUT_CONFIDENCE, 0.0);
        setOutputParameter(OUTPUT_TOKENS_USED, 0);
    }

    @Override
    protected void doExecute() throws MistralOcrException {
        log.info("Executing Extract Fields connector");
        ExtractFieldsResult result = client.extractFields(configuration);
        setOutputParameter(OUTPUT_EXTRACTED_FIELDS, result.extractedFields());
        setOutputParameter(OUTPUT_EXTRACTED_FIELDS_MAP, result.extractedFieldsMap());
        setOutputParameter(OUTPUT_PAGES, result.pages());
        setOutputParameter(OUTPUT_PAGES_MAP, result.pagesMap());
        setOutputParameter(OUTPUT_PAGE_COUNT, result.pageCount());
        setOutputParameter(OUTPUT_FIELD_COUNT, result.fieldCount());
        setOutputParameter(OUTPUT_CONFIDENCE, result.confidence());
        setOutputParameter(OUTPUT_TOKENS_USED, result.tokensUsed());
        log.info("Extract Fields connector executed successfully ({} fields)", result.fieldCount());
    }
}
