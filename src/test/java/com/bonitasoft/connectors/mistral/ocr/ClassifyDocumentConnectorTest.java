package com.bonitasoft.connectors.mistral.ocr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bonitasoft.connectors.mistral.ocr.model.ClassifyDocumentResult;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class ClassifyDocumentConnectorTest {

    @Mock
    private MistralOcrClient mockClient;

    private ClassifyDocumentConnector connector;
    private Map<String, Object> inputs;

    @BeforeEach
    void setUp() {
        connector = new ClassifyDocumentConnector();
        inputs = new HashMap<>();
        inputs.put("apiKey", "test-api-key");
        inputs.put("imageUrl", "https://example.com/doc.pdf");
        inputs.put("documentTypes", "invoice, receipt, contract, report");
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        var result = new ClassifyDocumentResult("invoice", 0.92, "Contains invoice header", "{}",
                java.util.List.of(), java.util.Map.of(), 1, 120);
        when(mockClient.classifyDocument(any())).thenReturn(result);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("documentType")).isEqualTo("invoice");
        assertThat(outputs.get("confidence")).isEqualTo(0.92);
        assertThat(outputs.get("reasoning")).isEqualTo("Contains invoice header");
        assertThat(outputs.get("pagesProcessed")).isEqualTo(120);
    }

    @Test
    void shouldFailValidationWhenDocumentTypesMissing() {
        inputs.remove("documentTypes");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("documentTypes");
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
    void shouldSetErrorOutputsOnFailure() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.classifyDocument(any())).thenThrow(new MistralOcrException("Classification failed"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat(outputs.get("errorMessage")).asString().contains("Classification failed");
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractMistralOcrConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }
}
