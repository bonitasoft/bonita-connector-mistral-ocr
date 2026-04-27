#!/usr/bin/env bash
# Starts a local mock of the Mistral API on http://localhost:8089
# Point Bonita Studio's connector baseUrl at http://localhost:8089/v1
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -q test-compile exec:java \
    -Dexec.classpathScope=test \
    -Dexec.mainClass=com.bonitasoft.connectors.mistral.ocr.MistralMockServer
