# CLAUDE.md

## Project Overview

This is an official Bonita connector for the Bonita BPM platform.

**Technology Stack:**
- Java 17
- Maven
- Bonita Runtime 10.2.0+

## Build and Test Commands

```bash
# Build all (skip tests)
./mvnw clean package

# Build with tests
./mvnw clean verify

# Run unit tests only
./mvnw test

# Run a single test class
./mvnw test -Dtest=MyConnectorTest
```

## Commit Message Format

Use conventional commits:
```
type(scope): description
```

Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`, `perf`

## Release Process

Releases are managed via GitHub Actions:
1. Run the "Release" workflow with the target version
2. Optionally set `update_marketplace: true` to publish to the Bonita marketplace
