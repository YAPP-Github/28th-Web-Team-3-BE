output "resource_names" {
  value = module.backend_environment.resource_names
}

output "secret_ids" {
  value = module.backend_environment.secret_ids
}

output "workload_identity_providers" {
  value = module.backend_environment.workload_identity_providers
}

output "service_accounts" {
  value = module.backend_environment.service_accounts
}

output "api_uri" {
  value = module.backend_environment.api_uri
}
