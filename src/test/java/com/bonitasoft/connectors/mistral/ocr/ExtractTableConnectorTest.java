package com.bonitasoft.connectors.mistral.ocr;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bonitasoft.connectors.mistral.ocr.model.ExtractTableResult;
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
class ExtractTableConnectorTest {

    @Mock
    private MistralOcrClient mockClient;

    private ExtractTableConnector connector;
    private Map<String, Object> inputs;

    @BeforeEach
    void setUp() {
        connector = new ExtractTableConnector();
        inputs = new HashMap<>();
        inputs.put("apiKey", "test-api-key");
        inputs.put("imageUrl", "https://example.com/table.pdf");
    }

    @Test
    void shouldExecuteSuccessfully() throws Exception {
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        injectMockClient();

        List<Map<String, String>> rows = List.of(
                Map.of("Name", "Alice", "Score", "95"),
                Map.of("Name", "Bob", "Score", "87")
        );
        var result = new ExtractTableResult("{}", rows, java.util.List.of(),
                java.util.Map.of(), 1, 2, 2, "[\"Name\",\"Score\"]", 200);
        when(mockClient.extractTable(any())).thenReturn(result);

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("tableDataList")).isEqualTo(rows);
        assertThat(outputs.get("rowCount")).isEqualTo(2);
        assertThat(outputs.get("columnCount")).isEqualTo(2);
        assertThat(outputs.get("pagesProcessed")).isEqualTo(200);
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

        when(mockClient.extractTable(any())).thenThrow(new MistralOcrException("Table extraction failed"));

        connector.executeBusinessLogic();

        Map<String, Object> outputs = connector.getOutputs();
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat(outputs.get("errorMessage")).asString().contains("Table extraction failed");
    }

    private void injectMockClient() throws Exception {
        var clientField = AbstractMistralOcrConnector.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(connector, mockClient);
    }
}
