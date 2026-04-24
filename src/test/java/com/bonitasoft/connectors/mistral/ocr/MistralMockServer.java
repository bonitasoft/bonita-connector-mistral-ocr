package com.bonitasoft.connectors.mistral.ocr;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Standalone Mistral API mock server for local testing.
 *
 * Start with scripts/run-mock-server.cmd or .sh.
 *
 * In Bonita Studio set the connector's baseUrl to: http://localhost:8089/v1
 *
 * /v1/ocr responds with:
 *  - pages: 3-page sample invoice in markdown
 *  - document_annotation: a JSON string with fields useful for Extract Fields,
 *    Classify Document and Extract Table (all 3 operations now go through /ocr).
 */
public class MistralMockServer {

    private static final int PORT = 8089;

    public static void main(String[] args) {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().port(PORT));
        registerOcrStub(server);
        server.start();

        System.out.println("==========================================================");
        System.out.println("  Mistral Mock Server running at http://localhost:" + PORT);
        System.out.println("  Use this in Bonita Studio as the connector baseUrl:");
        System.out.println("    http://localhost:" + PORT + "/v1");
        System.out.println("  Any apiKey value is accepted.");
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println("==========================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void registerOcrStub(WireMockServer server) {
        // document_annotation is a JSON *string*, so every inner quote is escaped twice:
        // once for the Java string literal and once for the text block that WireMock returns.
        String annotation = "{"
                + "\\\"document_type\\\": \\\"invoice\\\","
                + "\\\"confidence\\\": 0.95,"
                + "\\\"reasoning\\\": \\\"Contains invoice number and totals.\\\","
                + "\\\"scores\\\": {\\\"invoice\\\": 0.95, \\\"contract\\\": 0.03, \\\"other\\\": 0.02},"
                + "\\\"garage_name\\\": \\\"Garage Central SARL\\\","
                + "\\\"garage_address\\\": \\\"12 rue des Forges, 75010 Paris\\\","
                + "\\\"invoice_number\\\": \\\"INV-2026-042\\\","
                + "\\\"invoice_amount\\\": \\\"45.00 EUR\\\","
                + "\\\"invoice_date\\\": \\\"April 22, 2026\\\","
                + "\\\"headers\\\": [\\\"description\\\", \\\"qty\\\", \\\"price\\\"],"
                + "\\\"rows\\\": ["
                + "{\\\"description\\\": \\\"Widget A\\\", \\\"qty\\\": \\\"10\\\", \\\"price\\\": \\\"2.50\\\"},"
                + "{\\\"description\\\": \\\"Widget B\\\", \\\"qty\\\": \\\"5\\\", \\\"price\\\": \\\"4.00\\\"}"
                + "]"
                + "}";

        String body = """
            {
              "model": "mistral-ocr-latest",
              "pages": [
                {"index": 0, "markdown": "# Garage Central SARL\\n\\nInvoice #: INV-2026-042\\nDate: April 22, 2026\\nCustomer: ACME Corp"},
                {"index": 1, "markdown": "| Description | Qty | Price |\\n|---|---|---|\\n| Widget A | 10 | 2.50 |\\n| Widget B | 5 | 4.00 |\\n| Total |   | 45.00 |"},
                {"index": 2, "markdown": "Thank you for your business.\\nPayment due: 30 days."}
              ],
              "document_annotation": "%s",
              "usage_info": {"pages_processed": 3, "doc_size_bytes": 12345}
            }
            """.formatted(annotation);

        server.stubFor(post(urlEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
