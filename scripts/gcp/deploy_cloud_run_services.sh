#!/usr/bin/env bash

set -euo pipefail

required_variables=(
  ENVIRONMENT_PREFIX
  GCP_PROJECT_ID
  GCP_REGION
  ARTIFACT_REGISTRY_REPOSITORY
  IMAGE_URI
  DEPLOY_SERVICE_ACCOUNT
  CLOUD_RUN_SERVICE
  MISSION_WORKER_SERVICE
  MISSION_DISPATCHER_SERVICE
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Missing required environment variable: ${variable_name}" >&2
    exit 1
  fi
done

if [[ "${DEPLOY_SERVICE_ACCOUNT}" != *"@${GCP_PROJECT_ID}.iam.gserviceaccount.com" ]]; then
  echo "DEPLOY_SERVICE_ACCOUNT must belong to the selected GCP project." >&2
  exit 1
fi

expected_image_prefix="${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${ENVIRONMENT_PREFIX}-"
if [[ "${IMAGE_URI}" != "${expected_image_prefix}"* ]]; then
  echo "IMAGE_URI must point to the selected environment project and prefixed repository." >&2
  exit 1
fi

for service_name in "${CLOUD_RUN_SERVICE}" "${MISSION_WORKER_SERVICE}" "${MISSION_DISPATCHER_SERVICE}"; do
  if [[ "${service_name}" != "${ENVIRONMENT_PREFIX}-"* ]]; then
    echo "Cloud Run service must start with ${ENVIRONMENT_PREFIX}-: ${service_name}" >&2
    exit 1
  fi
done

# Terraform owns service accounts, runtime configuration, secret references, IAM,
# scaling and Scheduler. Application deployment changes only the immutable image.
for service_name in "${MISSION_WORKER_SERVICE}" "${MISSION_DISPATCHER_SERVICE}" "${CLOUD_RUN_SERVICE}"; do
  gcloud run services update "${service_name}" \
    --project "${GCP_PROJECT_ID}" \
    --region "${GCP_REGION}" \
    --image "${IMAGE_URI}" \
    --quiet
done
