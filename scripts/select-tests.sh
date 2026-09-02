#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-main}"
MAP_FILE="scripts/test-impact-map.yaml"

if ! command -v yq &> /dev/null; then
    echo "Error: yq is required but not installed." >&2
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
MATCHED_TESTS=()
MATCHED_API_TESTS=()
UNMATCHED_FILES=()

RULE_COUNT=$(yq '.rules | length' "$MAP_FILE")

while IFS= read -r file; do
    [ -z "$file" ] && continue
    FILE_MATCHED=false

    for ((i=0; i<RULE_COUNT; i++)); do
        PREFIX=$(yq ".rules[$i].path_prefix" "$MAP_FILE")

        if [[ "$file" == "$PREFIX"* ]]; then
            FILE_MATCHED=true

            RULE_RUN_ALL=$(yq ".rules[$i].run_all" "$MAP_FILE")
            if [ "$RULE_RUN_ALL" == "true" ]; then
                RUN_ALL=true
                continue
            fi

            TEST_COUNT=$(yq ".rules[$i].tests | length" "$MAP_FILE" 2>/dev/null || echo 0)
            if [ "$TEST_COUNT" != "null" ] && [ "$TEST_COUNT" -gt 0 ] 2>/dev/null; then
                while IFS= read -r t; do
                    [ -n "$t" ] && MATCHED_TESTS+=("$t")
                done < <(yq ".rules[$i].tests[]" "$MAP_FILE")
            fi

            API_TEST_COUNT=$(yq ".rules[$i].api_tests | length" "$MAP_FILE" 2>/dev/null || echo 0)
            if [ "$API_TEST_COUNT" != "null" ] && [ "$API_TEST_COUNT" -gt 0 ] 2>/dev/null; then
                while IFS= read -r t; do
                    [ -n "$t" ] && MATCHED_API_TESTS+=("$t")
                done < <(yq ".rules[$i].api_tests[]" "$MAP_FILE")
            fi
        fi
    done

    if [ "$FILE_MATCHED" == "false" ]; then
        UNMATCHED_FILES+=("$file")
    fi
done <<< "$CHANGED_FILES"

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

UNIQUE_TESTS=$(printf '%s\n' "${MATCHED_TESTS[@]-}" | sort -u | grep -v '^$' || true)
UNIQUE_API_TESTS=$(printf '%s\n' "${MATCHED_API_TESTS[@]-}" | sort -u | grep -v '^$' || true)

echo "DECISION: Selected tests" >&2
echo "" >&2
echo "Unit/Integration tests (main module):" >&2
echo "$UNIQUE_TESTS" | sed 's/^/  /' >&2
echo "" >&2
echo "API tests (api-tests module):" >&2
echo "$UNIQUE_API_TESTS" | sed 's/^/  /' >&2

echo "MAIN_TESTS=$(echo "$UNIQUE_TESTS" | paste -sd, -)"
echo "API_TESTS=$(echo "$UNIQUE_API_TESTS" | paste -sd, -)"