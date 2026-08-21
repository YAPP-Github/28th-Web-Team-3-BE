# Mission generation latency runbook

## Scope and safety

This runbook uses the safe `mission_generation_latency` application event. Its only metric labels are `stage`, `outcome`, and `generation_source`; `job_id` is log-only correlation. Do not add user identifiers, personalization, prompt/response data, task names, URLs, bodies, secrets, or raw exceptions to this event or its metric configuration.

## Before applying definitions

1. Obtain the deployed API, dispatcher, and worker Cloud Run service names from the deployment variables. Verify each with `CLOUD_RUN_SERVICE` before replacing the three placeholders in `observability/mission-generation/log-based-metric.yaml` and `dashboard.yaml`.
2. Review the rendered filter to confirm that it selects only those three Cloud Run services.
3. Apply the metric/dashboard only with separate deployment approval. This repository deliberately stores definitions only.

## Interpretation

The distribution metric is in milliseconds with explicit boundaries: 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000, 60000, and 120000.

- `dispatch`: outbox creation to successful Cloud Tasks publish.
- `queue`: outbox publish to worker receipt; `unpaired` is an explicit bounded outcome for an unmatched attempt.
- `retrieval`, `verification`, `ai_generation`, and `persistence`: worker stages.
- `worker_total`: worker receipt through its terminal handling for that attempt.
- `end_to_end`: job creation through the first terminal completion.

`duplicate`, `retry`, `skipped`, and `unpaired` are bounded outcomes; they must not be folded into a successful attempt.

Log-based metrics can have up to 10 minutes of collection delay in this operational workflow. Use them for diagnosis and SLO reporting, not immediate paging. Page from Cloud Tasks native depth/delay/count and Cloud Run request/startup/instance/concurrency metrics with a window of at least 5 minutes.

The current k6 p95 of 30 seconds includes polling and is only a provisional client-side guardrail, not a server-stage SLO.

## Worker warmth configuration

The production workflow deploys the worker with startup CPU boost and an explicit revision-level minimum of `0`, then applies the worker's service-level minimum of `1`. It also applies the dispatcher's service-level minimum of `0` and an explicit revision-level minimum of `0`. Do not combine a nonzero revision-level minimum with a service-level minimum: it can keep extra revisions warm and increase cost.

The workflow verifies the worker service minimum, dispatcher service minimum, latest worker revision's minimum, startup CPU boost, concurrency, private worker URL, and the Cloud Tasks invoker binding. Deployment itself still requires separate production approval.

After an approved deployment, use the latency event from the observability rollout to compare an idle period of at least 15 minutes with a warm baseline. Record request receipt to first application event and total generation latency separately; a warm worker reduces the normal scale-from-zero startup segment, but does not guarantee a particular AI-generation duration. Record at least 24 hours of Cloud Run billable instance time and cost before estimating the steady-state impact.

## Throughput rollout

The production workflow keeps worker container concurrency at `1` and defines the initial stage through deployment variables: queue dispatch rate=`1`, queue concurrent dispatches=`1`, worker max instances=`1`, and worker Hikari maximum pool size=`1` (minimum idle=`0`). It also uses a 60-second provider transport timeout without SDK retries, a 90-second Cloud Tasks dispatch deadline, and a 120-second Cloud Run worker timeout. The worker's service-level minimum of `1` and startup CPU boost remain required.

Do not set stage `2` or `3` values in repository defaults. After separate deployment and load-test approval, verify an approved Gemini quota/cost limit and the full DB/PgBouncer connection budget before raising all of queue rate, queue concurrency, and worker max instances together. The worker Hikari maximum pool must stay within that approved budget.

At each stage, run the approved scenario at least three times and record p50/p95/p99, Gemini 429, job failures, Cloud Tasks delay, Cloud Run instances, and DB/PgBouncer connections. Keep the worker attempt within 75 seconds. Return to the previous stable values on any 429, job failure, Cloud Tasks/Worker 5xx increase, DB/pool saturation, more than 20% p95/p99 regression, warm end-to-end p95 above 30 seconds, or approved quota/cost overrun. While request-time knowledge verification remains enabled, record that it can contribute to worker time and quota use.

## Rollback

For the worker-warmth rollback, use the same approved deployment path and set both services back to a service-level minimum of `0`:

```bash
gcloud run services update "${MISSION_WORKER_SERVICE}" --project "${GCP_PROJECT_ID}" --region "${GCP_REGION}" --min 0
gcloud run services update "${MISSION_DISPATCHER_SERVICE}" --project "${GCP_PROJECT_ID}" --region "${GCP_REGION}" --min 0
```

Then verify both service-level minima are `0` and the current worker revision's minimum is empty or `0`; leave startup CPU boost unchanged unless a separate latency/cost decision calls for changing it. The normal post-deploy check intentionally expects worker min=`1`, so do not use it to validate this rollback.

For a throughput rollback, use the same approved deployment path and restore queue dispatch rate and concurrent dispatches, worker max instances, and worker Hikari maximum pool to the previous stable stage (initially all `1`; minimum idle=`0`). Confirm the resulting queue and worker revision settings with the post-deploy checks. A provider timeout that is caught by the worker releases the lease through the existing retry path; a forced process termination can retain its current lease until recovery, so do not shorten the lease below the Cloud Run timeout without a separate idempotency design review.

For the observability rollback, disable the latency log filter and external metric/dashboard definitions first. Keep `worker_started_at` and `completed_at`: they are additive nullable columns and remain compatible with earlier application revisions.
