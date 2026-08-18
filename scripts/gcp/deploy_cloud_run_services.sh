#!/usr/bin/env bash
# Image-only deploy path for the three parallel dev services.
set -euo pipefail
readonly EXPECTED_PROJECT_ID=yapp-28th-web3-dev EXPECTED_REGION=asia-northeast3
readonly EXPECTED_API_SERVICE=dev-yapp-backend EXPECTED_WORKER_SERVICE=dev-mission-generation-worker EXPECTED_DISPATCHER_SERVICE=dev-mission-generation-dispatcher
for variable_name in GCP_PROJECT_ID GCP_REGION ARTIFACT_REGISTRY_REPOSITORY IMAGE_URI CLOUD_RUN_SERVICE MISSION_WORKER_SERVICE MISSION_DISPATCHER_SERVICE; do [[ -n "${!variable_name:-}" ]] || { echo "Missing ${variable_name}" >&2; exit 1; }; done
[[ "${GCP_PROJECT_ID}" == "${EXPECTED_PROJECT_ID}" && "${GCP_REGION}" == "${EXPECTED_REGION}" ]] || { echo 'Refusing a non-dev target' >&2; exit 1; }
[[ "${CLOUD_RUN_SERVICE}" == "${EXPECTED_API_SERVICE}" && "${MISSION_WORKER_SERVICE}" == "${EXPECTED_WORKER_SERVICE}" && "${MISSION_DISPATCHER_SERVICE}" == "${EXPECTED_DISPATCHER_SERVICE}" ]] || { echo 'Unexpected service allowlist' >&2; exit 1; }
[[ "${IMAGE_URI}" == "${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${ARTIFACT_REGISTRY_REPOSITORY}/${CLOUD_RUN_SERVICE}:"* ]] || { echo 'Image outside dev registry' >&2; exit 1; }
for service in "${MISSION_WORKER_SERVICE}" "${MISSION_DISPATCHER_SERVICE}" "${CLOUD_RUN_SERVICE}"; do
  gcloud run services describe "${service}" --project "${GCP_PROJECT_ID}" --region "${GCP_REGION}" --quiet >/dev/null
  gcloud run services update "${service}" --project "${GCP_PROJECT_ID}" --region "${GCP_REGION}" --image "${IMAGE_URI}" --quiet
done
