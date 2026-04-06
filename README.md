# Bonita Connector Template

This is a template repository for creating official Bonita connectors.

## Getting Started

1. Click **"Use this template"** on GitHub to create a new repository
2. Update the following placeholders:
   - `pom.xml`: Set `groupId`, `artifactId`, `name`, `description`
   - `.github/workflows/release.yml`: Replace `CHANGE_ME` with your `artifactId` and `bonitaMinVersion`
   - `CLAUDE.md`: Update with your connector's specific details
   - `README.md`: Replace this content with your connector's documentation

## What's Included

| File | Purpose |
|------|---------|
| `.github/workflows/build.yml` | CI build on push (compile, test, publish snapshots) |
| `.github/workflows/build-pr.yml` | CI build on pull requests |
| `.github/workflows/claude-code-review.yml` | Automated code review via Claude Code on PRs |
| `.github/workflows/claude.yml` | Interactive `@claude` mentions in issues, PRs, and reviews |
| `.github/workflows/release.yml` | Release workflow with opt-in marketplace notification |
| `.github/dependabot.yml` | Automated dependency updates |
| `pom.xml` | Maven project configuration |
| `CLAUDE.md` | Claude Code instructions for this repo |

## Release

```bash
# Trigger from GitHub Actions > Release workflow
# Set version and optionally enable marketplace update
```

## License

See [LICENSE](LICENSE).
