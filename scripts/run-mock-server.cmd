@echo off
REM Starts a local mock of the Mistral API on http://localhost:8089
REM Point Bonita Studio's connector baseUrl at http://localhost:8089/v1

pushd %~dp0..
call mvnw.cmd -q test-compile exec:java ^
    -Dexec.classpathScope=test ^
    -Dexec.mainClass=com.bonitasoft.connectors.mistral.ocr.MistralMockServer
popd
