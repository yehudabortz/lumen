#!/bin/sh
# Apply only when the GitHub plan/visibility supports rulesets. Does not change visibility or billing.
set -eu
cd "$(dirname "$0")/../.."
REPO="${1:-yehudabortz/lumen}"
apply_ruleset() {
  CONFIG=$1
  RULESET_FILTER=$2
  RULESET_ID=$(gh api "repos/$REPO/rulesets" --jq "$RULESET_FILTER") || {
    echo 'GitHub rulesets are unavailable. Use a supported plan or repository visibility before retrying.' >&2
    exit 1
  }
  if [ -n "$RULESET_ID" ]; then
    gh api --method PUT "repos/$REPO/rulesets/$RULESET_ID" --input "$CONFIG"
  else
    gh api --method POST "repos/$REPO/rulesets" --input "$CONFIG"
  fi
}
apply_ruleset .github/main-ruleset.json '.[] | select(.name == "Protect main") | .id'
apply_ruleset .github/owner-review-ruleset.json '.[] | select(.name == "Owner review for main") | .id'
gh api "repos/$REPO/rules/branches/main"
