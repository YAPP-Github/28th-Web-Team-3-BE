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

## Rollback

Disable the latency log filter and external metric/dashboard definitions first. Keep `worker_started_at` and `completed_at`: they are additive nullable columns and remain compatible with earlier application revisions.
