package com.bonitasoft.connectors.mistral.ocr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bonitasoft.connectors.mistral.ocr.model.ExtractFieldsResult;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class ExtractFieldsConnectorTest {

    @Mock
    private MistralOcrClient mockClient;

    private ExtractFieldsConnector connector;
    private Map<String, Object> inputs;

    @BeforeEach
    void setUp() {
        connector = new ExtractFieldsConnector();
        inputs = new HashMap<>();
        inputs.put("apiKey", "test-api-key");
        inputs.put("imageUrl", "https://example.com/doc.pdf");
        inputs.put("fieldsSchema", "{\"name\": \"string\", \"amount\": \"number\"}");
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        Map<String, Object> fieldsMap = Map.of("name", "John", "amount", 42);
        var result = new ExtractFieldsResult("{\"name\":\"John\",\"amount\":42}", fieldsMap, 2, 0.95, 150);
        when(mockClient.extractFields(any())).thenReturn(result);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("extractedFields")).isNotNull();
        assertThat(outputs.get("extractedFieldsMap")).isEqualTo(fieldsMap);
        assertThat(outputs.get("fieldCount")).isEqualTo(2);
        assertThat(outputs.get("confidence")).isEqualTo(0.95);
        assertThat(outputs.get("tokensUsed")).isEqualTo(150);
    }

    @Test
    void shouldFailValidationWhenFieldsSchemaMissing() {
        inputs.remove("fieldsSchema");
        connector.setInputParameters(inputs);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("fieldsSchema");
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

        when(mockClient.extractFields(any())).thenThrow(new MistralOcrException("Field extraction failed"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat(outputs.get("errorMessage")).asString().contains("Field extraction failed");
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractMistralOcrConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }
}
