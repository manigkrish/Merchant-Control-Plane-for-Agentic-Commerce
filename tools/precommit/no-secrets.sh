#!/usr/bin/env bash
set -euo pipefail

# Lightweight staged-content scanner.
# GitHub secret scanning + push protection is the primary defense,
# but this local check reduces accidental leaks before they leave your machine.

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

# Only scan staged changes (what you are about to commit).
STAGED_FILES="$(git diff --cached --name-only --diff-filter=ACM || true)"

if [[ -z "${STAGED_FILES}" ]]; then
  exit 0
fi

# Block committing .env files outright.
while IFS= read -r f; do
  if [[ "${f}" == ".env" || "${f}" == .env.* ]]; then
    fail "Refusing to commit ${f}. Use .env locally and keep it out of git."
  fi
done <<< "${STAGED_FILES}"

# Concatenate staged file contents (text only) and scan for common secret patterns.
# This is intentionally conservative; false positives are acceptable for a public repo.
TMP="$(mktemp)"
trap 'rm -f "${TMP}"' EXIT

while IFS= read -r f; do
  # Only scan files that exist in the index (avoid deleted).
  if git cat-file -e ":${f}" 2>/dev/null; then
    # Skip large/binary-like files by checking for NUL bytes
    if git show ":${f}" | LC_ALL=C grep -q $'\x00'; then
      continue
    fi
    echo "----- FILE: ${f} -----" >> "${TMP}"
    git show ":${f}" >> "${TMP}"
    echo >> "${TMP}"
  fi
done <<< "${STAGED_FILES}"

# Patterns: AWS Access Key, generic "secret=" assignments, OpenAI key prefix, private key blocks.
grep -nE 'AKIA[0-9A-Z]{16}' "${TMP}" && fail "Possible AWS access key detected (AKIA...)."
grep -nE 'ASIA[0-9A-Z]{16}' "${TMP}" && fail "Possible AWS temporary access key detected (ASIA...)."
grep -nE '-----BEGIN (RSA|EC|OPENSSH) PRIVATE KEY-----' "${TMP}" && fail "Private key material detected."
grep -nE '\b(sk-[A-Za-z0-9]{20,})\b' "${TMP}" && fail "Possible OpenAI API key detected (sk-...)."
grep -niE '\b(secret|password|token|api[_-]?key)\s*[:=]\s*[^\s]+' "${TMP}" && fail "Possible secret assignment detected."

exit 0
