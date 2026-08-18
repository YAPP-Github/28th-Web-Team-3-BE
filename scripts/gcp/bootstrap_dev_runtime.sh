#!/usr/bin/env bash
# Creates only the parallel dev runtime; it never modifies legacy yapp-backend.
set -euo pipefail

readonly PROJECT_ID=yapp-28th-web3-dev REGION=asia-northeast3 REPOSITORY=yapp-team3
readonly LEGACY_API_SERVICE=yapp-backend API_SERVICE=dev-yapp-backend
readonly WORKER_SERVICE=dev-mission-generation-worker DISPATCHER_SERVICE=dev-mission-generation-dispatcher
readonly QUEUE=dev-mission-generation SCHEDULER_JOB=dev-mission-generation-dispatch
readonly API_RUNTIME_SA=dev-api-runtime WORKER_RUNTIME_SA=dev-mission-worker-runtime
readonly DISPATCHER_RUNTIME_SA=dev-mission-dispatch-runtime TASKS_INVOKER_SA=dev-mission-tasks-invoker
readonly SCHEDULER_INVOKER_SA=dev-mission-scheduler-invoker DEPLOYER_SA=dev-backend-deployer

die() { echo "bootstrap-dev-runtime: $*" >&2; exit 1; }
sa_email() { printf '%s@%s.iam.gserviceaccount.com' "$1" "${PROJECT_ID}"; }
assert_target() {
  [[ "${PROJECT_ID}" == yapp-28th-web3-dev && "${REGION}" == asia-northeast3 ]] || die "unexpected target"
  for service in "${API_SERVICE}" "${WORKER_SERVICE}" "${DISPATCHER_SERVICE}"; do
    [[ "${service}" == dev-* && "${service}" != "${LEGACY_API_SERVICE}" ]] || die "service outside allowlist"
  done
}
ensure_sa() {
  gcloud iam service-accounts describe "$(sa_email "$1")" --project "${PROJECT_ID}" --quiet >/dev/null 2>&1 ||
    gcloud iam service-accounts create "$1" --project "${PROJECT_ID}" --display-name "Dev $1" --quiet >/dev/null
}
bind_sa_user() {
  gcloud iam service-accounts add-iam-policy-binding "$(sa_email "$1")" --project "${PROJECT_ID}" \
    --member "$2" --role roles/iam.serviceAccountUser --quiet >/dev/null
}
bind_secret_accessor() {
  gcloud secrets add-iam-policy-binding "$1" --project "${PROJECT_ID}" \
    --member "serviceAccount:$(sa_email "$2")" --role roles/secretmanager.secretAccessor --quiet >/dev/null
}
deploy_new_service() {
  local service="$1" account="$2" timeout="$3" concurrency="$4" max_instances="$5" min_instances="$6" envs="$7"
  if gcloud run services describe "${service}" --project "${PROJECT_ID}" --region "${REGION}" --quiet >/dev/null 2>&1; then
    [[ "$(gcloud run services describe "${service}" --project "${PROJECT_ID}" --region "${REGION}" --format='value(metadata.labels.managed-by)' --quiet)" == dev-bootstrap ]] || die "refusing unmanaged ${service}"
    return
  fi
  gcloud run deploy "${service}" --project "${PROJECT_ID}" --region "${REGION}" --image "${SEED_IMAGE}" \
    --service-account "$(sa_email "${account}")" --ingress all --no-allow-unauthenticated --cpu 1 --memory 1Gi \
    --timeout "${timeout}" --concurrency "${concurrency}" --max-instances "${max_instances}" --min-instances "${min_instances}" \
    --labels 'environment=dev,managed-by=dev-bootstrap,application=yapp-backend' --set-env-vars "${envs}" \
    --set-secrets 'SPRING_DATABASE_URL=SPRING_DATABASE_URL:latest,SPRING_DATABASE_USERNAME=SPRING_DATABASE_USER:latest,SPRING_DATABASE_PASSWORD=SPRING_DATABASE_PASSWORD:latest,JWT_SECRET=JWT_SECRET:latest' \
    --quiet >/dev/null
}

assert_target
for api in artifactregistry.googleapis.com cloudtasks.googleapis.com cloudscheduler.googleapis.com iam.googleapis.com iamcredentials.googleapis.com run.googleapis.com secretmanager.googleapis.com; do
  gcloud services enable "${api}" --project "${PROJECT_ID}" --quiet >/dev/null
done
gcloud artifacts repositories describe "${REPOSITORY}" --project "${PROJECT_ID}" --location "${REGION}" --quiet >/dev/null 2>&1 ||
  gcloud artifacts repositories create "${REPOSITORY}" --project "${PROJECT_ID}" --location "${REGION}" --repository-format docker --quiet >/dev/null

gcloud run services describe "${LEGACY_API_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --quiet >/dev/null || die "missing legacy seed source"
SEED_IMAGE="$(gcloud run services describe "${LEGACY_API_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --format='value(spec.template.spec.containers[0].image)' --quiet)"
[[ "${SEED_IMAGE}" == "${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}/"* ]] || die "legacy image is outside approved registry"

for account in "${API_RUNTIME_SA}" "${WORKER_RUNTIME_SA}" "${DISPATCHER_RUNTIME_SA}" "${TASKS_INVOKER_SA}" "${SCHEDULER_INVOKER_SA}" "${DEPLOYER_SA}"; do ensure_sa "${account}"; done
for secret in SPRING_DATABASE_URL SPRING_DATABASE_USER SPRING_DATABASE_PASSWORD; do
  gcloud secrets versions list "${secret}" --project "${PROJECT_ID}" --filter='state=ENABLED' --limit=1 --format='value(name)' --quiet | grep -q . || die "no enabled ${secret} version"
done
gcloud secrets describe JWT_SECRET --project "${PROJECT_ID}" --quiet >/dev/null 2>&1 || gcloud secrets create JWT_SECRET --project "${PROJECT_ID}" --replication-policy automatic --quiet >/dev/null
if ! gcloud secrets versions list JWT_SECRET --project "${PROJECT_ID}" --filter='state=ENABLED' --limit=1 --format='value(name)' --quiet | grep -q .; then
  openssl rand -base64 48 | gcloud secrets versions add JWT_SECRET --project "${PROJECT_ID}" --data-file=- --quiet >/dev/null
fi
for account in "${API_RUNTIME_SA}" "${WORKER_RUNTIME_SA}" "${DISPATCHER_RUNTIME_SA}"; do
  for secret in SPRING_DATABASE_URL SPRING_DATABASE_USER SPRING_DATABASE_PASSWORD JWT_SECRET; do bind_secret_accessor "${secret}" "${account}"; done
done

PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)' --quiet)"
TASKS_AGENT="service-${PROJECT_NUMBER}@gcp-sa-cloudtasks.iam.gserviceaccount.com"
SCHEDULER_AGENT="service-${PROJECT_NUMBER}@gcp-sa-cloudscheduler.iam.gserviceaccount.com"
bind_sa_user "${TASKS_INVOKER_SA}" "serviceAccount:$(sa_email "${DISPATCHER_RUNTIME_SA}")"
bind_sa_user "${TASKS_INVOKER_SA}" "serviceAccount:${TASKS_AGENT}"

gcloud tasks queues describe "${QUEUE}" --project "${PROJECT_ID}" --location "${REGION}" --quiet >/dev/null 2>&1 ||
  gcloud tasks queues create "${QUEUE}" --project "${PROJECT_ID}" --location "${REGION}" --max-concurrent-dispatches 1 --max-dispatches-per-second 1 --max-attempts 5 --min-backoff 10s --max-backoff 300s --max-doublings 16 --quiet >/dev/null
gcloud tasks queues add-iam-policy-binding "${QUEUE}" --project "${PROJECT_ID}" --location "${REGION}" --member "serviceAccount:$(sa_email "${DISPATCHER_RUNTIME_SA}")" --role roles/cloudtasks.enqueuer --quiet >/dev/null

deploy_new_service "${WORKER_SERVICE}" "${WORKER_RUNTIME_SA}" 600 1 20 0 'SPRING_PROFILES_ACTIVE=dev,APP_ROLE=mission-worker,SERVER_URL=https://placeholder.invalid,AI_ACTIVATION=off,MISSION_LIFECYCLE_SCHEDULER_ENABLED=false,JWT_ISSUER=yapp,JWT_AUDIENCE=yapp-client,JWT_ACCESS_TOKEN_TTL=PT3H,JWT_REFRESH_TOKEN_TTL=P14D'
deploy_new_service "${DISPATCHER_SERVICE}" "${DISPATCHER_RUNTIME_SA}" 300 1 20 0 "SPRING_PROFILES_ACTIVE=dev,APP_ROLE=mission-dispatcher,SERVER_URL=https://placeholder.invalid,AI_ACTIVATION=off,MISSION_LIFECYCLE_SCHEDULER_ENABLED=false,MISSION_GENERATION_DELIVERY_ENABLED=true,MISSION_GENERATION_DELIVERY_PROJECT_ID=${PROJECT_ID},MISSION_GENERATION_DELIVERY_LOCATION=${REGION},MISSION_GENERATION_DELIVERY_QUEUE=${QUEUE},MISSION_GENERATION_WORKER_URL=https://placeholder.invalid,MISSION_GENERATION_TASKS_OIDC_SERVICE_ACCOUNT=$(sa_email "${TASKS_INVOKER_SA}"),JWT_ISSUER=yapp,JWT_AUDIENCE=yapp-client,JWT_ACCESS_TOKEN_TTL=PT3H,JWT_REFRESH_TOKEN_TTL=P14D"
deploy_new_service "${API_SERVICE}" "${API_RUNTIME_SA}" 300 80 3 1 'SPRING_PROFILES_ACTIVE=dev,APP_ROLE=api,SERVER_URL=https://placeholder.invalid,AI_ACTIVATION=off,MISSION_GENERATION_DELIVERY_ENABLED=false,JWT_ISSUER=yapp,JWT_AUDIENCE=yapp-client,JWT_ACCESS_TOKEN_TTL=PT3H,JWT_REFRESH_TOKEN_TTL=P14D'

API_URL="$(gcloud run services describe "${API_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)' --quiet)"
WORKER_URL="$(gcloud run services describe "${WORKER_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)' --quiet)"
DISPATCHER_URL="$(gcloud run services describe "${DISPATCHER_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)' --quiet)"
for service in "${API_SERVICE}" "${WORKER_SERVICE}" "${DISPATCHER_SERVICE}"; do gcloud run services update "${service}" --project "${PROJECT_ID}" --region "${REGION}" --update-env-vars "SERVER_URL=${API_URL}" --quiet >/dev/null; done
gcloud run services update "${DISPATCHER_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --update-env-vars "MISSION_GENERATION_WORKER_URL=${WORKER_URL}" --quiet >/dev/null

gcloud run services add-iam-policy-binding "${API_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --member allUsers --role roles/run.invoker --quiet >/dev/null
gcloud run services add-iam-policy-binding "${WORKER_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --member "serviceAccount:$(sa_email "${TASKS_INVOKER_SA}")" --role roles/run.invoker --quiet >/dev/null
gcloud run services add-iam-policy-binding "${DISPATCHER_SERVICE}" --project "${PROJECT_ID}" --region "${REGION}" --member "serviceAccount:$(sa_email "${SCHEDULER_INVOKER_SA}")" --role roles/run.invoker --quiet >/dev/null

gcloud scheduler jobs describe "${SCHEDULER_JOB}" --project "${PROJECT_ID}" --location "${REGION}" --quiet >/dev/null 2>&1 ||
  gcloud scheduler jobs create http "${SCHEDULER_JOB}" --project "${PROJECT_ID}" --location "${REGION}" --schedule '* * * * *' --time-zone Etc/UTC --http-method POST --uri "${DISPATCHER_URL}/internal/mission-generation/dispatch" --oidc-service-account-email "$(sa_email "${SCHEDULER_INVOKER_SA}")" --oidc-token-audience "${DISPATCHER_URL}" --attempt-deadline 180s --quiet >/dev/null
gcloud scheduler jobs pause "${SCHEDULER_JOB}" --project "${PROJECT_ID}" --location "${REGION}" --quiet >/dev/null

WIF_PRINCIPAL="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-actions-pool/attribute.repository/YAPP-Github/28th-Web-Team-3-BE"
gcloud iam service-accounts add-iam-policy-binding "$(sa_email "${DEPLOYER_SA}")" --project "${PROJECT_ID}" --member "${WIF_PRINCIPAL}" --role roles/iam.workloadIdentityUser --quiet >/dev/null
gcloud artifacts repositories add-iam-policy-binding "${REPOSITORY}" --project "${PROJECT_ID}" --location "${REGION}" --member "serviceAccount:$(sa_email "${DEPLOYER_SA}")" --role roles/artifactregistry.writer --quiet >/dev/null
for account in "${API_RUNTIME_SA}" "${WORKER_RUNTIME_SA}" "${DISPATCHER_RUNTIME_SA}"; do bind_sa_user "${account}" "serviceAccount:$(sa_email "${DEPLOYER_SA}")"; done
for service in "${API_SERVICE}" "${WORKER_SERVICE}" "${DISPATCHER_SERVICE}"; do gcloud run services add-iam-policy-binding "${service}" --project "${PROJECT_ID}" --region "${REGION}" --member "serviceAccount:$(sa_email "${DEPLOYER_SA}")" --role roles/run.developer --quiet >/dev/null; done

for service in "${API_SERVICE}" "${WORKER_SERVICE}" "${DISPATCHER_SERVICE}"; do
  [[ "$(gcloud run services describe "${service}" --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.conditions[?type=Ready].status)' --quiet)" == True ]] || die "${service} is not Ready"
done
[[ "$(gcloud tasks queues describe "${QUEUE}" --project "${PROJECT_ID}" --location "${REGION}" --format='value(state)' --quiet)" == RUNNING ]] || die "queue is not running"
[[ "$(gcloud scheduler jobs describe "${SCHEDULER_JOB}" --project "${PROJECT_ID}" --location "${REGION}" --format='value(state)' --quiet)" == PAUSED ]] || die "Scheduler must remain paused"
gcloud projects get-iam-policy "${PROJECT_ID}" --flatten='bindings[].members' --filter="bindings.role:roles/cloudtasks.serviceAgent AND bindings.members:serviceAccount:${TASKS_AGENT}" --format='value(bindings.role)' --quiet | grep -qx 'roles/cloudtasks.serviceAgent' || die 'Cloud Tasks service agent role missing'
gcloud projects get-iam-policy "${PROJECT_ID}" --flatten='bindings[].members' --filter="bindings.role:roles/cloudscheduler.serviceAgent AND bindings.members:serviceAccount:${SCHEDULER_AGENT}" --format='value(bindings.role)' --quiet | grep -qx 'roles/cloudscheduler.serviceAgent' || die 'Cloud Scheduler service agent role missing'
echo 'Dev parallel runtime bootstrap completed; Scheduler remains paused.'
