SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

help:
	@echo "Targets:"
	@echo "  make hooks         - Configure git to use .githooks/"
	@echo "  make secret-scan    - Run local no-secrets scan (lightweight)"
	@echo "  make markdownlint   - Run markdownlint (requires Node+npx)"
	@echo "  make gitleaks       - Run gitleaks (requires gitleaks installed)"
	@echo "  make check          - Run Sprint-0 checks (best-effort local parity with CI)"
	@echo ""
	@echo "  make up             - Start local infra (docker compose)"
	@echo "  make down           - Stop local infra"
	@echo "  make ps             - Show local infra status"
	@echo "  make logs           - Tail local infra logs"
	@echo "  make restart        - Restart local infra"
	@echo ""
	@echo "  make test           - Run all Maven tests"
	@echo "  make run-gateway    - Run gateway-service (port 8080)"
	@echo "  make run-admin      - Run admin-service (port 8081)"

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

# ----------------------------
# Sprint 1: Local dev workflow
# ----------------------------

COMPOSE_FILE ?= infra/docker-compose/docker-compose.yml

.PHONY: up down ps logs restart test \
        install-gateway install-admin \
        run-gateway run-admin

up:
	docker compose -f $(COMPOSE_FILE) up -d
	$(MAKE) ps

down:
	docker compose -f $(COMPOSE_FILE) down

ps:
	docker compose -f $(COMPOSE_FILE) ps

logs:
	docker compose -f $(COMPOSE_FILE) logs -f --tail=200

restart: down up

test:
	mvn -q test

# Install only what the service needs (fast) so spring-boot:run can be executed
# from the service module without Maven trying to run on the parent aggregator POM.
install-gateway:
	mvn -q -DskipTests -pl libs/platform-web,services/gateway-service -am install

install-admin:
	mvn -q -DskipTests -pl libs/platform-web,services/admin-service -am install

run-gateway: install-gateway
	cd services/gateway-service && mvn -q spring-boot:run

run-admin: install-admin
	cd services/admin-service && mvn -q spring-boot:run
