package com.bonitasoft.connectors.mistral.ocr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bonitasoft.connectors.mistral.ocr.model.ExtractTextResult;
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
class ExtractTextConnectorTest {

    @Mock
    private MistralOcrClient mockClient;

    private ExtractTextConnector connector;
    private Map<String, Object> inputs;

    @BeforeEach
    void setUp() {
        connector = new ExtractTextConnector();
        inputs = new HashMap<>();
        inputs.put("apiKey", "test-api-key");
        inputs.put("imageUrl", "https://example.com/doc.pdf");
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        var result = new ExtractTextResult("Hello World", List.of("Hello", "World"), 2, 100);
        when(mockClient.extractText(any())).thenReturn(result);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("extractedText")).isEqualTo("Hello World");
        assertThat(outputs.get("pages")).isEqualTo(List.of("Hello", "World"));
        assertThat(outputs.get("pageCount")).isEqualTo(2);
        assertThat(outputs.get("tokensUsed")).isEqualTo(100);
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
        inputs.remove("imageUrl");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("documentBase64 or imageUrl");
    }

    @Test
    void shouldSetErrorOutputsOnFailure() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.extractText(any())).thenThrow(new MistralOcrException("API error"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat(outputs.get("errorMessage")).asString().contains("API error");
    }

    @Test
    void shouldApplyDefaultsForNullOptionalInputs() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        // Verify configuration was built with defaults
        assertThat(connector.configuration.getBaseUrl()).isEqualTo("https://api.mistral.ai/v1");
        assertThat(connector.configuration.getModel()).isEqualTo("mistral-ocr-latest");
        assertThat(connector.configuration.getConnectTimeout()).isEqualTo(30000);
        assertThat(connector.configuration.getReadTimeout()).isEqualTo(120000);
        assertThat(connector.configuration.isIncludePageSegmentation()).isTrue();
    }

    @Test
    void shouldAcceptDocumentBase64AsAlternative() throws Exception {
        inputs.remove("imageUrl");
        inputs.put("documentBase64", "dGVzdA==");
        connector.setInputParameters(inputs);

        // Should not throw
        connector.validateInputParameters();
    }

    @Test
    void shouldHandleUnexpectedException() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        when(mockClient.extractText(any())).thenThrow(new RuntimeException("Unexpected"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat(outputs.get("errorMessage")).asString().contains("Unexpected");
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractMistralOcrConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }
}
