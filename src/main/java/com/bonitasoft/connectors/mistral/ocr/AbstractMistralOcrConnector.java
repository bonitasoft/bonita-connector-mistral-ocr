package com.bonitasoft.connectors.mistral.ocr;

import lombok.extern.slf4j.Slf4j;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.Map;

/**
 * Abstract base connector for all Mistral OCR operations.
 */
@Slf4j
public abstract class AbstractMistralOcrConnector extends AbstractConnector {

    protected static final String OUTPUT_SUCCESS = "success";
    protected static final String OUTPUT_ERROR_MESSAGE = "errorMessage";

    protected MistralOcrConfiguration configuration;
    protected MistralOcrClient client;

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        try {
            this.configuration = buildConfiguration();
            validateConfiguration(this.configuration);
        } catch (IllegalArgumentException e) {
            throw new ConnectorValidationException(this, e.getMessage());
        }
    }

    @Override
    public void connect() throws ConnectorException {
        try {
            this.client = new MistralOcrClient(this.configuration);
            log.info("Mistral OCR connector connected successfully");
        } catch (MistralOcrException e) {
            throw new ConnectorException("Failed to connect: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() throws ConnectorException {
        this.client = null;
    }

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        setOutputParameter(OUTPUT_SUCCESS, false);
        setOutputParameter(OUTPUT_ERROR_MESSAGE, "");
        initializeOutputs();
        try {
            doExecute();
            setOutputParameter(OUTPUT_SUCCESS, true);
        } catch (MistralOcrException e) {
            log.error("Mistral OCR connector execution failed: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in Mistral OCR connector: {}", e.getMessage(), e);
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (e.getCause() != null) {
                detail += " caused by " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            }
            setOutputParameter(OUTPUT_ERROR_MESSAGE, "Unexpected error: " + detail);
        }
    }

    protected abstract void doExecute() throws MistralOcrException;

    protected abstract MistralOcrConfiguration buildConfiguration();

    /**
     * Pre-fill connector-specific outputs with neutral defaults so that a failing
     * execution still produces a value for every output declared in the .def.
     * Without this, Bonita raises SExpressionEvaluationException when a .proc
     * maps an output that the connector never got to set.
     */
    protected abstract void initializeOutputs();

    /**
     * Validates connection parameters shared across all operations.
     */
    protected void validateConfiguration(MistralOcrConfiguration config) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException("apiKey is mandatory");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl is mandatory");
        }
    }

    /** Expose output parameters for testing (package-private). */
    Map<String, Object> getOutputs() {
        return getOutputParameters();
    }

    protected String readStringInput(String name) {
        Object value = getInputParameter(name);
        return value != null ? value.toString() : null;
    }

    protected String readStringInput(String name, String defaultValue) {
        String value = readStringInput(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    protected Boolean readBooleanInput(String name, boolean defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? (Boolean) value : defaultValue;
    }

    protected Integer readIntegerInput(String name, int defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? ((Number) value).intValue() : defaultValue;
    }

    protected Long readLongInput(String name, long defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? ((Number) value).longValue() : defaultValue;
    }

    /** Read an optional Integer input. Returns null when the .proc leaves it blank. */
    protected Integer readOptionalInteger(String name) {
        Object value = getInputParameter(name);
        return value != null ? ((Number) value).intValue() : null;
    }
}
