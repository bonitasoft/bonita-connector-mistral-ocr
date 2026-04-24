package com.bonitasoft.connectors.mistral.ocr;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bonitasoft.connectors.mistral.ocr.model.ClassifyDocumentResult;
import com.bonitasoft.connectors.mistral.ocr.model.ExtractFieldsResult;
import com.bonitasoft.connectors.mistral.ocr.model.ExtractTableResult;
import com.bonitasoft.connectors.mistral.ocr.model.ExtractTextResult;
import com.bonitasoft.connectors.mistral.ocr.model.ProcessBatchResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validates MistralOcrClient against a WireMock-stubbed Mistral API.
 *
 * Covers:
 *  - Request body matches the official Mistral API contract
 *    (document.type = "document_url" / "image_url", data URI for base64)
 *  - Response parsing for all 5 operations
 */
class MistralOcrClientWireMockTest {

    private WireMockServer wireMock;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port() + "/v1";
    }

    @AfterEach
    void stopServer() {
        wireMock.stop();
    }

    @Test
    void extractText_withImageUrl_shouldSendDocumentUrlTypeAndParsePages() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "model": "mistral-ocr-latest",
                              "pages": [
                                {"index": 0, "markdown": "# Page one\\nHello"},
                                {"index": 1, "markdown": "Page two text"}
                              ],
                              "usage": {"total_tokens": 42}
                            }
                            """)));

        MistralOcrConfiguration config = baseConfig()
                .imageUrl("https://example.com/doc.pdf")
                .mimeType("application/pdf")
                .build();
        MistralOcrClient client = new MistralOcrClient(config);

        ExtractTextResult result = client.extractText(config);

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.pages()).containsExactly("# Page one\nHello", "Page two text");
        assertThat(result.extractedText()).contains("Page one").contains("Page two");
        assertThat(result.tokensUsed()).isEqualTo(42);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("mistral-ocr-latest")))
                .withRequestBody(matchingJsonPath("$.document.type", equalTo("document_url")))
                .withRequestBody(matchingJsonPath("$.document.document_url",
                        equalTo("https://example.com/doc.pdf"))));
    }

    @Test
    void extractText_withBase64Pdf_shouldSendDataUriInsideDocumentUrl() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"pages\":[{\"markdown\":\"ok\"}],\"usage\":{\"total_tokens\":1}}")));

        MistralOcrConfiguration config = baseConfig()
                .documentBase64("JVBERi0xLjQK")
                .mimeType("application/pdf")
                .build();
        new MistralOcrClient(config).extractText(config);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.document.type", equalTo("document_url")))
                .withRequestBody(matchingJsonPath("$.document.document_url",
                        equalTo("data:application/pdf;base64,JVBERi0xLjQK"))));
    }

    @Test
    void extractText_withImageMimeType_shouldSendImageUrlType() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"pages\":[],\"usage\":{\"total_tokens\":0}}")));

        MistralOcrConfiguration config = baseConfig()
                .imageUrl("https://example.com/photo.png")
                .mimeType("image/png")
                .build();
        new MistralOcrClient(config).extractText(config);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.document.type", equalTo("image_url")))
                .withRequestBody(matchingJsonPath("$.document.image_url",
                        equalTo("https://example.com/photo.png"))));
    }

    @Test
    void extractFields_fromPdf_shouldSendOcrWithAnnotationAndParseDocumentAnnotation() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "pages":[{"markdown":"Invoice content"}],
                              "document_annotation":"{\\"invoice_number\\":\\"INV-001\\",\\"total\\":123.45,\\"confidence\\":0.95}",
                              "usage_info":{"pages_processed":1}
                            }
                            """)));

        MistralOcrConfiguration config = baseConfig()
                .documentBase64("JVBERi0xLjQK")
                .mimeType("application/pdf")
                .fieldsSchema("{\"type\":\"object\",\"properties\":{\"invoice_number\":{\"type\":\"string\"},\"total\":{\"type\":\"number\"}}}")
                .build();

        ExtractFieldsResult result = new MistralOcrClient(config).extractFields(config);

        assertThat(result.extractedFieldsMap()).containsEntry("invoice_number", "INV-001");
        assertThat(result.extractedFieldsMap()).containsEntry("total", 123.45);
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.tokensUsed()).isEqualTo(1);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.document.type", equalTo("document_url")))
                .withRequestBody(matchingJsonPath("$.document_annotation_format.type", equalTo("json_schema")))
                .withRequestBody(matchingJsonPath("$.document_annotation_format.json_schema.name",
                        equalTo("ExtractedFields"))));
    }

    @Test
    void extractFields_fromImage_shouldSendImageUrlType() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "pages":[{"markdown":"Invoice from image"}],
                              "document_annotation":"{\\"invoice_number\\":\\"INV-IMG-007\\"}",
                              "usage_info":{"pages_processed":1}
                            }
                            """)));

        MistralOcrConfiguration config = baseConfig()
                .documentBase64("aW1hZ2U=")
                .mimeType("image/png")
                .fieldsSchema("{\"type\":\"object\"}")
                .build();

        ExtractFieldsResult result = new MistralOcrClient(config).extractFields(config);

        assertThat(result.extractedFieldsMap()).containsEntry("invoice_number", "INV-IMG-007");

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.document.type", equalTo("image_url")))
                .withRequestBody(matchingJsonPath("$.document.image_url",
                        equalTo("data:image/png;base64,aW1hZ2U="))));
    }

    @Test
    void classifyDocument_shouldParseTypeConfidenceAndScores() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "pages":[{"markdown":"content"}],
                              "document_annotation":"{\\"document_type\\":\\"invoice\\",\\"confidence\\":0.92,\\"reasoning\\":\\"Has invoice number and totals\\",\\"scores\\":{\\"invoice\\":0.92,\\"contract\\":0.05}}",
                              "usage_info":{"pages_processed":1}
                            }
                            """)));

        MistralOcrConfiguration config = baseConfig()
                .imageUrl("https://example.com/doc.pdf")
                .mimeType("application/pdf")
                .documentTypes("invoice, contract, other")
                .includeReasoning(true)
                .build();

        ClassifyDocumentResult result = new MistralOcrClient(config).classifyDocument(config);

        assertThat(result.documentType()).isEqualTo("invoice");
        assertThat(result.confidence()).isEqualTo(0.92);
        assertThat(result.reasoning()).contains("invoice number");
        assertThat(result.allScores()).contains("\"invoice\":0.92");

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.document_annotation_format.json_schema.name",
                        equalTo("DocumentClassification"))));
    }

    @Test
    void extractTable_shouldParseRowsAndHeaders() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "pages":[{"markdown":"table content"}],
                              "document_annotation":"{\\"headers\\":[\\"item\\",\\"qty\\"],\\"rows\\":[{\\"item\\":\\"Pen\\",\\"qty\\":\\"2\\"},{\\"item\\":\\"Paper\\",\\"qty\\":\\"5\\"}]}",
                              "usage_info":{"pages_processed":1}
                            }
                            """)));

        MistralOcrConfiguration config = baseConfig()
                .imageUrl("https://example.com/table.pdf")
                .mimeType("application/pdf")
                .columnHeaders("item, qty")
                .build();

        ExtractTableResult result = new MistralOcrClient(config).extractTable(config);

        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.columnCount()).isEqualTo(2);
        assertThat(result.tableDataList()).hasSize(2);
        assertThat(result.tableDataList().get(0)).containsEntry("item", "Pen").containsEntry("qty", "2");

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.document_annotation_format.json_schema.name",
                        equalTo("TableExtraction"))));
    }

    @Test
    void processBatch_withPageRange_shouldSendZeroIndexedPagesArray() throws Exception {
        // User asks for pages 2..4 (1-indexed in the .proc). The connector must
        // forward pages [1, 2, 3] to Mistral because the API is 0-indexed.
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "pages":[
                                {"index":1,"markdown":"page 2 content"},
                                {"index":2,"markdown":"page 3 content"},
                                {"index":3,"markdown":"page 4 content"}
                              ],
                              "usage_info":{"pages_processed":3}
                            }
                            """)));

        MistralOcrConfiguration config = baseConfig()
                .documentBase64("JVBERi0xLjQK")
                .startPage(2)
                .endPage(4)
                .build();

        ProcessBatchResult result = new MistralOcrClient(config).processBatch(config);

        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.pagesMap()).containsKeys(2, 3, 4);
        assertThat(result.pagesMap().get(2)).isEqualTo("page 2 content");
        assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0);

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/ocr"))
                .withRequestBody(matchingJsonPath("$.pages[0]", equalTo("1")))
                .withRequestBody(matchingJsonPath("$.pages[1]", equalTo("2")))
                .withRequestBody(matchingJsonPath("$.pages[2]", equalTo("3"))));
    }

    @Test
    void extractText_on500_shouldRetryAndEventuallyFail() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse().withStatus(500).withBody("server down")));

        MistralOcrConfiguration config = baseConfig()
                .imageUrl("https://example.com/doc.pdf")
                .maxRetries(1)
                .build();

        MistralOcrClient client = new MistralOcrClient(config);

        assertThatThrownBy(() -> client.extractText(config))
                .isInstanceOf(MistralOcrException.class)
                .hasMessageContaining("HTTP 500");

        wireMock.verify(2, postRequestedFor(urlEqualTo("/v1/ocr")));
    }

    private MistralOcrConfiguration.MistralOcrConfigurationBuilder baseConfig() {
        return MistralOcrConfiguration.builder()
                .apiKey("test-key")
                .baseUrl(baseUrl)
                .model("mistral-ocr-latest")
                .connectTimeout(5_000)
                .readTimeout(10_000)
                .maxRetries(0);
    }
}
