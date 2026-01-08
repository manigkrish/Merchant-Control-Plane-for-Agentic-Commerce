# Public Repo Guardrails (Day-1 Hygiene)

This repository is public. The security posture is “assume everything will be read.”

## Non-negotiables
- No secrets committed — ever.
- No long-lived AWS keys stored in GitHub (Actions secrets or repo secrets).
- Use GitHub secret scanning and push protection.
- Use environment variables and local .env files (ignored by git) for local-only values.

## GitHub settings checklist
Repository Settings → Code security and analysis:
- Enable Secret scanning
- Enable Push protection

Repository Settings → Branches:
- Protect main branch
- Require PR to merge
- Require status checks to pass

## Local developer guardrails
- Pre-commit hook runs a local no-secrets scan:
  - tools/precommit/no-secrets.sh
- Terraform state is never committed:
  - .gitignore blocks *.tfstate*

## AWS credential policy
- Sprint 1 CI does not assume AWS roles (build/test only).
- Later deploy sprints will use GitHub Actions OIDC for short-lived credentials (docs/security/ci-oidc.md).
- Local dev should prefer short-lived credentials (AWS SSO / IAM Identity Center) where possible.

## Incident response (if a secret leaks)
1. Immediately rotate/revoke the credential.
2. Remove secret from git history (requires care), and open a postmortem.
3. Add pattern to prevent reoccurrence.
