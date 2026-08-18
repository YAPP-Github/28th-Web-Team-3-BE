resource "google_secret_manager_secret" "runtime" {
  for_each = local.secret_ids

  project   = var.project_id
  secret_id = each.value
  labels    = local.labels

  replication {
    auto {}
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.required]
}

resource "google_secret_manager_secret_iam_member" "sync_version_adder" {
  for_each = google_secret_manager_secret.runtime

  project   = var.project_id
  secret_id = each.value.secret_id
  role      = "roles/secretmanager.secretVersionAdder"
  member    = google_service_account.environment["secret_sync"].member
}

locals {
  api_required_secrets = toset(concat(
    ["spring_database_url", "spring_database_user", "spring_database_password", "jwt_secret"],
    var.enable_policy_import ? ["policy_import_token", "youth_policy_api_key"] : [],
  ))

  worker_required_secrets = toset(concat(
    ["spring_database_url", "spring_database_user", "spring_database_password", "jwt_secret"],
    var.enable_ai ? ["google_genai_api_key", "naver_blog_client_id", "naver_blog_client_secret"] : [],
  ))

  dispatcher_required_secrets = toset([
    "spring_database_url",
    "spring_database_user",
    "spring_database_password",
    "jwt_secret",
  ])

  runtime_secret_access = merge(
    { for secret in local.api_required_secrets : "api:${secret}" => {
      service_account = "api_runtime"
      secret          = secret
    } },
    { for secret in local.worker_required_secrets : "worker:${secret}" => {
      service_account = "worker_runtime"
      secret          = secret
    } },
    { for secret in local.dispatcher_required_secrets : "dispatcher:${secret}" => {
      service_account = "dispatcher_runtime"
      secret          = secret
    } },
  )
}

resource "google_secret_manager_secret_iam_member" "runtime_accessor" {
  for_each = local.runtime_secret_access

  project   = var.project_id
  secret_id = google_secret_manager_secret.runtime[each.value.secret].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.environment[each.value.service_account].member
}
