#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-main}"
TEST_OUTPUT_FILE="${2:-}"

if [ -z "$TEST_OUTPUT_FILE" ] || [ ! -f "$TEST_OUTPUT_FILE" ]; then
    echo "Usage: ./scripts/triage-failure.sh <base-ref> <path-to-test-output-file>"
    echo "Example: ./scripts/triage-failure.sh main /tmp/failure.log"
    exit 1
fi

DIFF_FILE=$(mktemp)
git diff "${BASE_REF}"...HEAD > "$DIFF_FILE"

echo "=================================================="
echo "Triage: analyzing failure against diff vs ${BASE_REF}"
echo "=================================================="
echo ""

python3 scripts/triage-failure.py "$TEST_OUTPUT_FILE" "$DIFF_FILE"

rm -f "$DIFF_FILE"