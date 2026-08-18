output "resource_names" {
  description = "Deterministic prefixed resource names."
  value       = local.resource_names
}

output "secret_ids" {
  description = "Secret Manager identifiers. Values are never managed by Terraform."
  value       = local.secret_ids
}

output "workload_identity_providers" {
  description = "Dedicated provider resource names by GitHub workflow authority."
  value       = { for key, provider in google_iam_workload_identity_pool_provider.github : key => provider.name }
}

output "service_accounts" {
  description = "Environment-specific GitHub and runtime service accounts."
  value       = { for key, account in google_service_account.environment : key => account.email }
}

output "api_uri" {
  description = "Public API URI after runtime services are enabled."
  value       = var.enable_runtime_services ? google_cloud_run_v2_service.api[0].uri : null
}

output "worker_uri" {
  description = "Mission worker URI after runtime services are enabled."
  value       = var.enable_runtime_services ? google_cloud_run_v2_service.worker[0].uri : null
}

output "dispatcher_uri" {
  description = "Mission dispatcher URI after runtime services are enabled."
  value       = var.enable_runtime_services ? google_cloud_run_v2_service.dispatcher[0].uri : null
}
