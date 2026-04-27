# Mistral OCR Connector — Testing Guide

End-to-end instructions to validate the Mistral OCR connector in Bonita Studio **2026.1**.

---

## Prerequisites

- Bonita Studio 2026.1 installed and runnable
- A valid **Mistral API key** (https://console.mistral.ai/api-keys)
- This repo cloned and built (`./mvnw clean verify` produces the zips in `target/`)

---

## Step 1 — Verify the fix without Bonita (recommended)

A live smoke test is included (`MistralOcrSmokeTest`). It calls the real Mistral OCR API with a public PDF and validates the HTTP layer independently from the Bonita runtime. Run it **before** touching Studio — if this fails, the connector will also fail inside Bonita.

```bash
# Linux/macOS
export MISTRAL_API_KEY=your_key_here
./mvnw test -Dtest=MistralOcrSmokeTest

# Windows PowerShell
$env:MISTRAL_API_KEY="your_key_here"
.\mvnw.cmd test -Dtest=MistralOcrSmokeTest

# Windows cmd
set MISTRAL_API_KEY=your_key_here
mvnw.cmd test -Dtest=MistralOcrSmokeTest
```

Expected output:
```
=== Mistral OCR Smoke Test ===
Pages: 30
Tokens used: ...
First 500 chars:
Lecture Notes on ...
```

If this passes, the HTTP layer is correct and any remaining issues are only about wiring inside Bonita.

---

## Step 2 — Import the connector into Bonita Studio 2026.1

1. Open Bonita Studio 2026.1.
2. Create (or open) a diagram.
3. Menu: **Development → Connectors → Import connector**.
4. Select the zip you want (use `bonita-connector-mistral-ocr-1.0.0-beta.1-extract-text-impl.zip` for the first test).
5. Studio will register the definition **`Mistral OCR - Extract Text [BETA]`** under category **Mistral OCR**.

Repeat for the other 4 zips if you want to test all operations:
- `...-extract-fields-impl.zip`
- `...-classify-document-impl.zip`
- `...-extract-table-impl.zip`
- `...-process-batch-impl.zip`

Alternative: use the `...-all.zip` to register all 5 at once (some Studio versions accept this bundle directly).

---

## Step 3 — Build a minimal test process

Goal: a one-step process that calls `Extract Text` on a public PDF and prints the result.

### 3.1 Create the diagram
- New diagram `MistralOcrSmoke`.
- Keep the default pool with one **Service Task** named `extractText`.

### 3.2 Declare a process variable for the API key
- Pool properties → **Data → Process variables → Add**:
  - Name: `mistralApiKey`
  - Type: `String`
  - Default value: *(leave empty; we'll set it at instantiation)*
- Also add:
  - Name: `pdfUrl`, Type: `String`, Default: `"https://arxiv.org/pdf/2201.04234"`
  - Name: `extractedText`, Type: `String`
  - Name: `pageCount`, Type: `Integer`

### 3.3 Attach the connector to the Service Task
- Select the `extractText` task → **Execution → Connectors in → Add**.
- Choose **Mistral OCR - Extract Text [BETA]** → Next.
- Connection page:
  - **API Key**: expression `mistralApiKey` (Script → Groovy → `mistralApiKey`)
  - Leave **Base URL**, **Model**, timeouts at default.
- Document page:
  - **Document (Base64)**: leave empty.
  - **Image URL**: expression `pdfUrl`.
  - **MIME Type**: `"application/pdf"` (Constant).
  - **Include Page Segmentation**: leave checked.
  - **Language**: leave empty.
- **Output operations**: map connector outputs to process variables:
  - `extractedText` → Takes value of → `extractedText`
  - `pageCount` → Takes value of → `pageCount`

### 3.4 Show the result
Easiest options:
- Add a **Human task** after `extractText` with a form that displays `extractedText` and `pageCount`, OR
- Use a Groovy connector-out on the same task logging `extractedText.take(500)` to the Bonita log.

---

## Step 4 — Configure the API key as a parameter

The cleanest approach for secrets is a **process parameter**:
- Pool → **Execution → Parameters → Add**:
  - Name: `mistralApiKey.default` (or similar)
- Bind the process variable `mistralApiKey` to that parameter in a Groovy initializer, or set the variable default via **Configure** before running.

For a quick test you can simply type the API key in the process variable `mistralApiKey` default value — just **do not commit** it.

---

## Step 5 — Deploy and run

1. Click **Run** in Studio (this starts the embedded Tomcat + UI Designer).
2. Open the Bonita Portal (default http://localhost:8080/bonita).
3. Start an instance of `MistralOcrSmoke`.
4. Watch the task execute. On success:
   - `extractedText` contains the full OCR'd text.
   - `pageCount` > 0.
5. On failure: the connector sets `success=false` and `errorMessage` with details. Check Studio console logs for the full HTTP response.

---

## Step 6 — Troubleshoot common issues

| Symptom | Likely cause | Fix |
|---|---|---|
| `ConnectorValidationException: apiKey is mandatory` | API key expression evaluates to null/empty | Check process variable is set and the expression returns a non-null String |
| `Mistral API error (HTTP 401)` | Wrong/expired API key | Regenerate at console.mistral.ai |
| `Mistral API error (HTTP 422)` | Malformed request body | Should not happen anymore after this fix; re-check you imported the rebuilt zip |
| `NoClassDefFoundError: com/fasterxml/jackson/...` | Jackson missing from the zip classpath | Rebuild — the fix changed `*:jar` → `*.jar` in the assemblies |
| Connector runs but `extractedText` is empty | PDF URL not accessible from the server | Test the URL returns 200 from the host running Bonita |
| Timeouts | Large PDFs (>20 pages) | Increase `readTimeout` (ms) in the connector Connection page |

---

## Step 7 — Test other operations (optional)

**Classify Document**: same document URL; add `documentTypes = "invoice, purchase_order, contract, other"`. Output `documentType` should be a string in that list.

**Extract Fields**: provide `fieldsSchema = '{ "invoice_number": "string", "total": "number", "date": "string" }'` on an invoice. Output `extractedFieldsMap` is a `Map<String,Object>`.

**Extract Table**: provide `columnHeaders = "description, quantity, price"` on a document with a table. Output `tableDataList` is a `List<Map<String,String>>`.

**Process Batch**: same as Extract Text but with `startPage` + `endPage` for page ranges on multi-page PDFs.

---

## What was fixed (background)

The original code sent a non-standard request body to `POST /v1/ocr`:
```json
{ "document": { "type": "base64", "data": "...", "mime_type": "application/pdf" } }
```
The Mistral API expects a discriminated union on `type`:
```json
{ "document": { "type": "document_url", "document_url": "data:application/pdf;base64,..." } }
```
Also, the assembly descriptor contained the typo `<include>*:jar</include>` (should be `*.jar`), so the connector zips shipped without the connector JAR and Jackson — causing `ClassNotFoundException` at runtime. Both are fixed in this build.
