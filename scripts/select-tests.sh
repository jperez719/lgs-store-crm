#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-main}"
MAP_FILE="scripts/test-impact-map.yaml"

if ! command -v yq &> /dev/null; then
    echo "Error: yq is required but not installed. Install with: brew install yq" >&2
    exit 1
fi

echo "Comparing against: ${BASE_REF}" >&2
CHANGED_FILES=$(git diff --name-only "${BASE_REF}"...HEAD)

if [ -z "$CHANGED_FILES" ]; then
    echo "No changed files detected. Nothing to select." >&2
    exit 0
fi

echo "Changed files:" >&2
echo "$CHANGED_FILES" | sed 's/^/  /' >&2
echo "" >&2

RUN_ALL=false
declare -a MATCHED_TESTS=()
declare -a MATCHED_API_TESTS=()
declare -a UNMATCHED_FILES=()

RULE_COUNT=$(yq '.rules | length' "$MAP_FILE")

while IFS= read -r file; do
    [ -z "$file" ] && continue
    FILE_MATCHED=false

    for ((i=0; i<RULE_COUNT; i++)); do
        PREFIX=$(yq -r ".rules[$i].path_prefix" "$MAP_FILE")

        if [[ "$file" == "$PREFIX"* ]]; then
            FILE_MATCHED=true

            RULE_RUN_ALL=$(yq -r ".rules[$i].run_all // false" "$MAP_FILE")
            if [ "$RULE_RUN_ALL" == "true" ]; then
                RUN_ALL=true
                continue
            fi

            while IFS= read -r t; do
                [ -n "$t" ] && MATCHED_TESTS+=("$t")
            done < <(yq -r ".rules[$i].tests[]? // empty" "$MAP_FILE")

            while IFS= read -r t; do
                [ -n "$t" ] && MATCHED_API_TESTS+=("$t")
            done < <(yq -r ".rules[$i].api_tests[]? // empty" "$MAP_FILE")
        fi
    done

    if [ "$FILE_MATCHED" == "false" ]; then
        UNMATCHED_FILES+=("$file")
    fi
done <<< "$CHANGED_FILES"

# Safety net: any changed file with NO matching rule at all forces run_all.
# An unmapped file is exactly the "we don't confidently know" case.
if [ "${#UNMATCHED_FILES[@]}" -gt 0 ]; then
    echo "Unmapped files detected (no rule matched):" >&2
    printf '  %s\n' "${UNMATCHED_FILES[@]}" >&2
    echo "Defaulting to RUN_ALL for safety." >&2
    echo "" >&2
    RUN_ALL=true
fi

if [ "$RUN_ALL" == "true" ]; then
    echo "DECISION: RUN_ALL" >&2
    echo "RUN_ALL"
    exit 0
fi

UNIQUE_TESTS=$(printf '%s\n' "${MATCHED_TESTS[@]}" | sort -u | grep -v '^$' || true)
UNIQUE_API_TESTS=$(printf '%s\n' "${MATCHED_API_TESTS[@]}" | sort -u | grep -v '^$' || true)

echo "DECISION: Selected tests" >&2
echo "" >&2
echo "Unit/Integration tests (main module):" >&2
echo "$UNIQUE_TESTS" | sed 's/^/  /' >&2
echo "" >&2
echo "API tests (api-tests module):" >&2
echo "$UNIQUE_API_TESTS" | sed 's/^/  /' >&2

# Machine-readable output on stdout, everything above went to stderr.
# This lets the script be safely piped/captured: `SELECTION=$(./select-tests.sh)`
echo "MAIN_TESTS=$(echo "$UNIQUE_TESTS" | paste -sd, -)"
echo "API_TESTS=$(echo "$UNIQUE_API_TESTS" | paste -sd, -)"