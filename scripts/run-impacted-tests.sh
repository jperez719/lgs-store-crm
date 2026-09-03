#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-main}"

echo "=================================================="
echo "Determining impacted tests against: ${BASE_REF}"
echo "=================================================="

SELECTION=$(./scripts/select-tests.sh "$BASE_REF")
echo ""

eval "$SELECTION"

if [ "$SELECTION" == "RUN_ALL" ]; then
    echo "Running FULL test suite (main module)..."
    mvn test

    echo ""
    echo "Running FULL API test suite (self-contained)..."
    (cd api-tests && mvn test -DselfContained=true -DAPP_IMAGE="${APP_IMAGE:-lgs-store-crm:latest}")
    exit 0
fi

if [ -n "${MAIN_TESTS:-}" ]; then
    echo "Running selected main module tests: ${MAIN_TESTS}"
    mvn test -Dtest="${MAIN_TESTS}"
else
    echo "No main module tests selected."
fi

echo ""

if [ -n "${API_TESTS:-}" ]; then
    echo "Running selected API tests: ${API_TESTS}"
    (cd api-tests && mvn test -Dtest="${API_TESTS}" -DselfContained=true -DAPP_IMAGE="${APP_IMAGE:-lgs-store-crm:latest}")
else
    echo "No API tests selected."
fi