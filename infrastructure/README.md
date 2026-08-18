# Backend Infrastructure

Backend GCP infrastructure is managed with Terraform under [`terraform`](./terraform/README.md).

Responsibilities are intentionally separated:

- Terraform owns APIs, Artifact Registry, IAM, Workload Identity Federation, Secret Manager containers, Cloud Tasks, Cloud Scheduler, and stable Cloud Run service configuration.
- GitHub Actions owns secret-version synchronization and application image deployment.
- Cloud Run reads runtime secrets only from GCP Secret Manager.

Never commit credentials, Terraform state, variable files containing real values, plan files, or command output containing private infrastructure identifiers.
