package com.bonitasoft.connectors.mistral.ocr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bonitasoft.connectors.mistral.ocr.model.ProcessBatchResult;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class ProcessBatchConnectorTest {

    @Mock
    private MistralOcrClient mockClient;

    private ProcessBatchConnector connector;
    private Map<String, Object> inputs;

    @BeforeEach
    void setUp() {
        connector = new ProcessBatchConnector();
        inputs = new HashMap<>();
        inputs.put("apiKey", "test-api-key");
        inputs.put("documentBase64", "dGVzdA==");
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        var pagesMap = new java.util.LinkedHashMap<Integer, String>();
        pagesMap.put(1, "Page 1");
        pagesMap.put(2, "Page 2");
        var result = new ProcessBatchResult("Page 1\n\nPage 2", List.of("Page 1", "Page 2"),
                pagesMap, 2, 4, 250, 1500L);
        when(mockClient.processBatch(any())).thenReturn(result);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("fullText")).isEqualTo("Page 1\n\nPage 2");
        assertThat(outputs.get("pages")).isEqualTo(List.of("Page 1", "Page 2"));
        assertThat(outputs.get("pagesMap")).isEqualTo(pagesMap);
        assertThat(outputs.get("pageCount")).isEqualTo(2);
        assertThat(outputs.get("totalWordCount")).isEqualTo(4);
        assertThat(outputs.get("tokensUsed")).isEqualTo(250);
        assertThat(outputs.get("processingTimeMs")).isEqualTo(1500L);
    }

    @Test
    void shouldFailValidationWhenApiKeyMissing() {
        inputs.remove("apiKey");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void shouldFailValidationWhenNoDocumentProvided() {
        inputs.remove("documentBase64");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("documentBase64 or imageUrls");
    }

    @Test
    void shouldAcceptImageUrlsAsAlternative() throws Exception {
        inputs.remove("documentBase64");
        inputs.put("imageUrls", "https://example.com/page1.pdf,https://example.com/page2.pdf");
        connector.setInputParameters(inputs);

        // Should not throw
        connector.validateInputParameters();
    }

    @Test
    void shouldSetErrorOutputsOnFailure() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.processBatch(any())).thenThrow(new MistralOcrException("Batch processing failed"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat(outputs.get("errorMessage")).asString().contains("Batch processing failed");
    }

    @Test
    void shouldApplyDefaultReadTimeout() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        assertThat(connector.configuration.getReadTimeout()).isEqualTo(300000);
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractMistralOcrConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }
}
