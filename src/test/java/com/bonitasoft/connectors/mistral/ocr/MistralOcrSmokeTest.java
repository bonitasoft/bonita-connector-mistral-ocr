package com.bonitasoft.connectors.mistral.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.bonitasoft.connectors.mistral.ocr.model.ExtractTextResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Live smoke test against the real Mistral OCR API.
 * Runs only when MISTRAL_API_KEY is set in the environment.
 *
 * Run with:
 *   MISTRAL_API_KEY=xxx ./mvnw test -Dtest=MistralOcrSmokeTest
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_API_KEY", matches = ".+")
class MistralOcrSmokeTest {

    private static final String SAMPLE_PDF_URL = "https://arxiv.org/pdf/2201.04234";

    @Test
    void extractText_fromPublicPdfUrl_shouldReturnPages() throws Exception {
        String apiKey = System.getenv("MISTRAL_API_KEY");
        MistralOcrConfiguration config = MistralOcrConfiguration.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.mistral.ai/v1")
                .model("mistral-ocr-latest")
                .imageUrl(SAMPLE_PDF_URL)
                .mimeType("application/pdf")
                .connectTimeout(30_000)
                .readTimeout(120_000)
                .maxRetries(2)
                .build();

        MistralOcrClient client = new MistralOcrClient(config);
        ExtractTextResult result = client.extractText(config);

        System.out.println("=== Mistral OCR Smoke Test ===");
        System.out.println("Pages: " + result.pageCount());
        System.out.println("Tokens used: " + result.pagesProcessed());
        System.out.println("First 500 chars:\n" + safeSubstring(result.extractedText(), 500));

        assertThat(result).isNotNull();
        assertThat(result.pageCount()).isGreaterThan(0);
        assertThat(result.extractedText()).isNotBlank();
    }

    private static String safeSubstring(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
