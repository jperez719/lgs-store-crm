#!/usr/bin/env bash
set -euo pipefail

# --- Usage check ---
if [ -z "${1:-}" ]; then
    echo "Usage: ./scripts/deploy.sh <image-tag>"
    echo "Example: ./scripts/deploy.sh v3.2-my-feature"
    exit 1
fi

TAG="$1"
IMAGE="lgs-store-crm:${TAG}"
COMPOSE_FILE="docker-compose.yml"
K8S_APP_DEPLOYMENT="k8s/app-deployment.yaml"
NAMESPACE="store-crm"
DEPLOYMENT_NAME="store-crm-app"

echo "=================================================="
echo "Deploying tag: ${TAG}"
echo "=================================================="

# --- 1. Build the image ---
echo ""
echo "[1/5] Building Docker image: ${IMAGE}"
docker build -t "${IMAGE}" .

# --- 2. Update docker-compose.yml ---
echo ""
echo "[2/5] Updating ${COMPOSE_FILE} to use ${IMAGE}"
# Matches a line like: "    image: lgs-store-crm:anything" under the app service
# and replaces only the tag portion.
sed -i.bak "s|image: lgs-store-crm:.*|image: lgs-store-crm:${TAG}|" "${COMPOSE_FILE}"
rm -f "${COMPOSE_FILE}.bak"

# --- 3. Update k8s/app-deployment.yaml ---
echo ""
echo "[3/5] Updating ${K8S_APP_DEPLOYMENT} to use ${IMAGE}"
sed -i.bak "s|image: lgs-store-crm:.*|image: lgs-store-crm:${TAG}|" "${K8S_APP_DEPLOYMENT}"
rm -f "${K8S_APP_DEPLOYMENT}.bak"

# --- 4. Apply to Kubernetes ---
echo ""
echo "[4/5] Applying updated Deployment to Kubernetes (namespace: ${NAMESPACE})"
kubectl apply -f "${K8S_APP_DEPLOYMENT}"

echo ""
echo "Waiting for rollout to complete..."
kubectl rollout status deployment "${DEPLOYMENT_NAME}" -n "${NAMESPACE}"

# --- 5. Summary ---
echo ""
echo "[5/5] Deploy complete."
echo ""
echo "Docker Compose is now configured to use: ${IMAGE}"
echo "  (run 'docker compose up -d' to start it locally with this tag)"
echo ""
echo "Kubernetes Deployment '${DEPLOYMENT_NAME}' is now running: ${IMAGE}"
kubectl get pods -n "${NAMESPACE}" -l app=store-crm-app