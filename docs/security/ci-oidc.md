# CI Authentication to AWS via GitHub Actions OIDC (No Long-Lived Keys)

Goal: GitHub Actions workflows authenticate to AWS without storing AWS access keys as GitHub secrets.

## Why
- Long-lived keys are frequently leaked and hard to rotate safely.
- OIDC enables short-lived, scoped credentials issued at workflow runtime.

## Approach (high level)
1. Create an AWS IAM OIDC identity provider that trusts GitHub.
2. Create an IAM role with a trust policy allowing your repo to assume the role.
3. In GitHub Actions, use aws-actions/configure-aws-credentials with role-to-assume.

## Trust policy scoping
Constrain by:
- GitHub org/user and repo name
- Branch (main) and/or environment
- Workflow file name (optional)

## Sprint 1 status (build + test only)
Sprint 1 does not deploy to AWS and does not assume AWS roles from GitHub Actions.
OIDC-based AWS access is planned for later deployment sprints only.

## Minimum trust-policy constraints (when enabled later)
When enabling GitHub OIDC → AWS role assumption, the IAM trust policy MUST be scoped to:

- Audience: `sts.amazonaws.com`
- Repository: only this repo (no org-wide wildcards)
- Ref: `refs/heads/main` only
- Workflow identity (recommended): restrict by workflow file when practical

Example condition pattern:
- `token.actions.githubusercontent.com:aud == "sts.amazonaws.com"`
- `token.actions.githubusercontent.com:sub` matches:
- `repo:manigkrish/Merchant-Control-Plane-for-Agentic-Commerce:ref:refs/heads/main`

## Deployment phases
- Sprint 0: document posture; no AWS deploy yet.
- Sprint 1: build + test only; no AWS auth.
- Later: Terraform creates OIDC provider + role + least-privilege policies; CI/CD assumes roles only for deploy workflows.

## References
- GitHub: Configuring OIDC in AWS
  https://docs.github.com/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
- AWS blog: Using IAM roles with GitHub Actions OIDC
  https://aws.amazon.com/blogs/security/use-iam-roles-to-connect-github-actions-to-actions-in-aws/
