package com.bonitasoft.connectors.mistral.ocr;

import com.bonitasoft.connectors.mistral.ocr.model.ExtractTableResult;
import lombok.extern.slf4j.Slf4j;

/**
 * [BETA] Extract table data from a document using Mistral Vision.
 */
@Slf4j
public class ExtractTableConnector extends AbstractMistralOcrConnector {

    static final String INPUT_API_KEY = "apiKey";
    static final String INPUT_BASE_URL = "baseUrl";
    static final String INPUT_MODEL = "model";
    static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    static final String INPUT_READ_TIMEOUT = "readTimeout";
    static final String INPUT_DOCUMENT_BASE64 = "documentBase64";
    static final String INPUT_IMAGE_URL = "imageUrl";
    static final String INPUT_MIME_TYPE = "mimeType";
    static final String INPUT_COLUMN_HEADERS = "columnHeaders";
    static final String INPUT_TABLE_HINT = "tableHint";
    static final String INPUT_PAGE_NUMBER = "pageNumber";

    static final String OUTPUT_TABLE_DATA = "tableData";
    static final String OUTPUT_TABLE_DATA_LIST = "tableDataList";
    static final String OUTPUT_ROW_COUNT = "rowCount";
    static final String OUTPUT_COLUMN_COUNT = "columnCount";
    static final String OUTPUT_DETECTED_HEADERS = "detectedHeaders";
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
                .columnHeaders(readStringInput(INPUT_COLUMN_HEADERS))
                .tableHint(readStringInput(INPUT_TABLE_HINT))
                .pageNumber(readIntegerInput(INPUT_PAGE_NUMBER, 1))
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
        log.info("Executing Extract Table connector");
        ExtractTableResult result = client.extractTable(configuration);
        setOutputParameter(OUTPUT_TABLE_DATA, result.tableData());
        setOutputParameter(OUTPUT_TABLE_DATA_LIST, result.tableDataList());
        setOutputParameter(OUTPUT_ROW_COUNT, result.rowCount());
        setOutputParameter(OUTPUT_COLUMN_COUNT, result.columnCount());
        setOutputParameter(OUTPUT_DETECTED_HEADERS, result.detectedHeaders());
        setOutputParameter(OUTPUT_TOKENS_USED, result.tokensUsed());
        log.info("Extract Table connector executed successfully ({} rows x {} cols)",
                result.rowCount(), result.columnCount());
    }
}
