# Dev GCP parallel runtime

This dev-only path replaces the unmerged Terraform-only design in commit `74b5440`. Do not apply that workflow or merge it as part of this migration. This branch does not modify production code, workflow, project, or resources.

| Alternative | Decision |
| --- | --- |
| Change legacy `yapp-backend` in place | Rejected: it breaks the required rollback boundary. |
| Create only a parallel API | Rejected: it cannot run dispatcher → Cloud Tasks → worker. |
| Create parallel API, worker, dispatcher | Selected: keeps legacy intact and matches the production topology. |

Bootstrap creates only `dev-yapp-backend`, `dev-mission-generation-worker`, and `dev-mission-generation-dispatcher`. The API is public; worker/dispatcher invoker permissions are scoped to task/scheduler identities. Queue rate is one concurrent task and one request per second with five attempts and 10–300 second retry. Scheduler is created paused.

`SERVER_URL` is a normal Cloud Run environment variable. Bootstrap uses a non-routable placeholder for the initial revision, retrieves the new API URL locally, then updates the three services. It never creates or references a `SERVER_URL` secret.

The existing dev DB secrets remain untouched: `SPRING_DATABASE_URL` maps to the same env name, `SPRING_DATABASE_USER` maps to `SPRING_DATABASE_USERNAME`, and `SPRING_DATABASE_PASSWORD` maps to the same env name. A `JWT_SECRET` version is generated only if none exists, without reading or printing a secret. AI and policy-import are disabled, so their secret references are absent.

## Bootstrap and rollback

An authorized local provisioning administrator runs `scripts/gcp/bootstrap_dev_runtime.sh`. Required one-time authority is API enablement, registry/service-account creation, IAM, Cloud Run/Tasks/Scheduler configuration, and first JWT secret creation. These broad permissions are not granted to GitHub Actions or runtime identities.

The script checks the exact dev target, seed image source, secret-version presence, Ready conditions, IAM, queue state, Google service-agent roles, and Scheduler paused state. It never mutates/deletes legacy `yapp-backend`.

Rollback is non-destructive: leave Scheduler paused and legacy unchanged. Revert a new service to a prior Ready revision only after an explicit recovery decision; do not delete services or queues.

## GitHub dev Environment

Set these **Environment variables** only after bootstrap succeeds: `GCP_PROJECT_ID`, `GCP_REGION`, `ARTIFACT_REGISTRY_REPOSITORY`, `CLOUD_RUN_SERVICE`, `MISSION_WORKER_SERVICE`, `MISSION_DISPATCHER_SERVICE`, `DEPLOY_WORKLOAD_IDENTITY_PROVIDER`, `DEPLOY_SERVICE_ACCOUNT`, `DATABASE_ISOLATION_CONFIRMED=true`, and finally `RUNTIME_SERVICES_READY=true`.

The workflow builds/pushes one image and only updates image revisions for the three allowlisted services. It cannot modify runtime settings, IAM, secret references, Scheduler, legacy API, or prod. If a GitHub-side secret lifecycle is needed, configure `JWT_SECRET` as a **dev Environment secret**; no secret value belongs in this repository.

## Verification

1. Run `bash -n scripts/gcp/bootstrap_dev_runtime.sh scripts/gcp/deploy_cloud_run_services.sh`.
2. Bootstrap, then check the three Ready services, public API health, service IAM, queue, and paused Scheduler without publishing URLs.
3. A manual Scheduler force-run is optional only after confirming a harmless no-job condition in the isolated dev DB. Immediately pause it again and verify `PAUSED`; this is the Scheduler OIDC → dispatcher verification.
4. After Environment setup, push/merge to `dev` and verify all three revisions use that commit SHA without publishing image URLs.
