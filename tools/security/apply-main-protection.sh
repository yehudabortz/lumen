#!/bin/sh
# Apply only when the GitHub plan/visibility supports rulesets. Does not change visibility or billing.
set -eu
cd "$(dirname "$0")/../.."
REPO="${1:-yehudabortz/lumen}"
RULESET_ID=$(gh api "repos/$REPO/rulesets" --jq '.[] | select(.name == "Protect main") | .id') || {
  echo 'GitHub rulesets are unavailable. Keep the repository private as requested; upgrade its plan before retrying.' >&2
  exit 1
}
if [ -n "$RULESET_ID" ]; then
  gh api --method PUT "repos/$REPO/rulesets/$RULESET_ID" --input .github/main-ruleset.json
else
  gh api --method POST "repos/$REPO/rulesets" --input .github/main-ruleset.json
fi
gh api "repos/$REPO/rules/branches/main"
