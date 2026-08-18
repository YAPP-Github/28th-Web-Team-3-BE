module "backend_environment" {
  source = "../../modules/backend-environment"

  environment              = "prod"
  project_id               = var.project_id
  region                   = var.region
  state_bucket_name        = var.state_bucket_name
  github_branch            = "main"
  bootstrap_image_uri      = var.bootstrap_image_uri
  enable_runtime_services  = var.enable_runtime_services
  enable_ai                = var.enable_ai
  enable_policy_import     = var.enable_policy_import
  server_url               = var.server_url
  jwt_issuer               = var.jwt_issuer
  jwt_audience             = var.jwt_audience
  api_min_instances        = 1
  background_min_instances = 0
  scheduler_paused         = var.scheduler_paused
  deletion_protection      = true
}
