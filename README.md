# Bonita Mistral OCR Connector

Bonita connectors for the [Mistral AI OCR API](https://docs.mistral.ai/capabilities/OCR/) — five operations covering text extraction, structured field extraction, document classification, table extraction, and multi-page batch processing. PDFs and images are both supported (selected automatically from the `mimeType` input).

> **Status:** beta (`1.0.1-beta.1`). Not yet on the marketplace.

## Operations

| Connector | Definition id | Endpoint | Use case |
|---|---|---|---|
| Extract Text | `com.bonitasoft.connectors.mistral.ocr.ExtractTextDefinition` | `POST /v1/ocr` | Pure OCR — return the markdown text of a document. |
| Process Batch | `…ProcessBatchDefinition` | `POST /v1/ocr` (with `pages` filter) | Same as Extract Text but with a 1-indexed page range and extra metrics (`totalWordCount`, `processingTimeMs`). |
| Extract Fields | `…ExtractFieldsDefinition` | `POST /v1/ocr` + `document_annotation_format` | Structured field extraction driven by a JSON Schema. |
| Classify Document | `…ClassifyDocumentDefinition` | `POST /v1/ocr` + `document_annotation_format` | Pick the document type from a user-defined list, optionally with reasoning. |
| Extract Table | `…ExtractTableDefinition` | `POST /v1/ocr` + `document_annotation_format` | Pull a tabular structure (`headers` + `rows`) out of a document. |

All five accept either a base64 input (`documentBase64` + `mimeType`) or a URL (`imageUrl`). The connector picks the API discriminator (`document_url` for documents, `image_url` for images) automatically.

## Inputs (common to all operations)

| Input | Type | Required | Default | Notes |
|---|---|---|---|---|
| `apiKey` | String | yes | — | Your Mistral API key. |
| `baseUrl` | String | no | `https://api.mistral.ai/v1` | Override for proxies or local mock servers. |
| `model` | String | no | `mistral-ocr-latest` (Extract Text / Process Batch) or `pixtral-large-latest` (Fields / Classify / Table) | Never overridden silently. |
| `connectTimeout` | Integer | no | 30 000 ms | |
| `readTimeout` | Integer | no | 120 000 ms (300 000 for Process Batch) | |
| `documentBase64` | String | one of base64/url | — | Raw base64 (no `data:` prefix; the connector adds it). |
| `imageUrl` (or `imageUrls` for Process Batch) | String | one of base64/url | — | Public URL or already-formed `data:` URI. |
| `mimeType` | String | no | `application/pdf` | Picks the API discriminator (`document_url` vs `image_url`). |
| `startPage` | Integer | no | — | **1-indexed**, optional. The connector converts to 0-indexed before calling Mistral. Must be set together with `endPage`. |
| `endPage` | Integer | no | — | **1-indexed inclusive**. |

## Outputs (common to all operations)

| Output | Type | Description |
|---|---|---|
| `pages` | List\<String\> | Markdown of each processed page, in API response order (0-indexed list). |
| `pagesMap` | Map\<Integer, String\> | Same content keyed by 1-indexed page number. Use this in Groovy when you want page → content lookups. |
| `pageCount` | Integer | `pages.size()` — number of pages actually returned by Mistral. |
| `pagesProcessed` | Integer | The `usage_info.pages_processed` value reported by the API. Equal to `pageCount` in normal conditions; differs when the API caches or short-circuits. |
| `success` | Boolean | `true` if the call returned without exception. Always set. |
| `errorMessage` | String | `""` on success; the exception message on failure. Always set. |

Operation-specific outputs (the ones declared in each `.def` on top of the common block):

- **Extract Text**: `extractedText` (full text concatenated).
- **Process Batch**: `fullText`, `totalWordCount`, `processingTimeMs`.
- **Extract Fields**: `extractedFields` (the raw JSON string), `extractedFieldsMap` (parsed `Map`), `fieldCount`, `confidence`.
- **Classify Document**: `documentType`, `confidence`, `reasoning`, `allScores` (JSON of per-type scores).
- **Extract Table**: `tableData` (raw JSON), `tableDataList` (parsed rows as `List<Map<String,String>>`), `rowCount`, `columnCount`, `detectedHeaders`.

## Build and Test

```bash
./mvnw clean verify              # full build + tests (42 tests)
./mvnw test -Dtest=MistralOcrClientWireMockTest   # body-shape + parsing tests against WireMock
MISTRAL_API_KEY=... ./mvnw test -Dtest=MistralOcrSmokeTest   # opt-in live test against api.mistral.ai
```

A standalone WireMock server is included so Studio can run the connector locally without a real API key:

```bash
scripts/run-mock-server.cmd       # Windows
scripts/run-mock-server.sh        # Unix
```

Point the connector's `baseUrl` to `http://localhost:8089/v1` in your `.proc`. Any `apiKey` is accepted by the mock.

## Breaking changes from 1.0.0-beta.1

> **Read this if you already use the connector.**

- **Process Batch page range was off-by-one**: the previous version sent 1-indexed `pages` to a 0-indexed API, dropping one page on every range. If you had a workaround that passed `startPage=0, endPage=N-1`, that is now incorrect — switch to natural 1-indexed values (`startPage=1, endPage=N`).
- **`tokensUsed` output renamed to `pagesProcessed`** in all five operations. The underlying value (read from `usage_info.pages_processed` in the API response) was always counting pages, never tokens. `.proc` files mapping the old `tokensUsed` output must be updated.
- **`pageNumber` input removed from Extract Table** — it was vestigial. Use `startPage` / `endPage` instead.
- **All five operations now go through `POST /v1/ocr`**. Extract Fields / Classify / Extract Table previously used `/chat/completions` with vision content; that path rejected PDFs with HTTP 422. The new path supports both PDFs and images.
- **Page range support is new** on Extract Text, Extract Fields, Classify, Extract Table. Leaving both `startPage` and `endPage` blank preserves the previous "process the whole document" behaviour.

## License

See [LICENSE](LICENSE).
