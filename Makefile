SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

help:
	@echo "Targets:"
	@echo "  make hooks         - Configure git to use .githooks/"
	@echo "  make secret-scan    - Run local no-secrets scan (lightweight)"
	@echo "  make markdownlint   - Run markdownlint (requires Node+npx)"
	@echo "  make gitleaks       - Run gitleaks (requires gitleaks installed)"
	@echo "  make check          - Run Sprint-0 checks (best-effort local parity with CI)"

hooks:
	git config core.hooksPath .githooks
	@echo "Configured git hooksPath to .githooks"

secret-scan:
	tools/precommit/no-secrets.sh

markdownlint:
	@if command -v npx >/dev/null 2>&1; then \
		npx -y markdownlint-cli2 "**/*.md" --config ".markdownlint.json"; \
	else \
		echo "SKIP: npx not found. Install Node.js to run markdownlint locally (CI will run it)."; \
	fi

gitleaks:
	@if command -v gitleaks >/dev/null 2>&1; then \
		gitleaks detect --redact --no-git; \
	else \
		echo "SKIP: gitleaks not found. Install gitleaks to run locally (CI will run it)."; \
	fi

check: secret-scan markdownlint gitleaks
	@echo "Sprint 0 checks completed (see any SKIP messages above)."
