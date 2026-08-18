data "google_project" "current" {
  project_id = var.project_id
}

resource "google_project_service" "required" {
  for_each = local.required_apis

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "backend" {
  project       = var.project_id
  location      = var.region
  repository_id = local.resource_names.artifact_repository
  description   = "${var.environment} backend container images"
  format        = "DOCKER"
  mode          = "STANDARD_REPOSITORY"
  labels        = local.labels

  depends_on = [google_project_service.required]
}

resource "google_service_account" "environment" {
  for_each = local.service_account_ids

  project      = var.project_id
  account_id   = each.value
  display_name = "${title(var.environment)} ${replace(each.key, "_", " ")}"
  description  = "Managed by Terraform for the ${var.environment} backend environment."

  depends_on = [google_project_service.required]
}

resource "google_iam_workload_identity_pool" "github" {
  for_each = local.github_identities

  project                   = var.project_id
  workload_identity_pool_id = each.value.pool_id
  display_name              = "${title(var.environment)} ${replace(each.key, "_", " ")}"
  description               = "Dedicated ${each.key} GitHub identity pool for ${var.environment}."

  depends_on = [google_project_service.required]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  for_each = local.github_identities

  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github[each.key].workload_identity_pool_id
  workload_identity_pool_provider_id = each.value.provider_id
  display_name                       = "${title(var.environment)} ${replace(each.key, "_", " ")}"
  description                        = "Trusts one repository environment branch and exact workflow."

  attribute_mapping = {
    "google.subject"                = "assertion.sub"
    "attribute.actor"               = "assertion.actor"
    "attribute.ref"                 = "assertion.ref"
    "attribute.repository"          = "assertion.repository"
    "attribute.repository_owner"    = "assertion.repository_owner"
    "attribute.repository_owner_id" = "assertion.repository_owner_id"
    "attribute.workflow_ref"        = "assertion.workflow_ref"
    "attribute.environment"         = "assertion.environment"
  }

  attribute_condition = "assertion.repository=='${var.github_repository}' && assertion.repository_owner_id=='${var.github_repository_owner_id}' && assertion.ref=='refs/heads/${var.github_branch}' && assertion.environment=='${var.environment}' && assertion.workflow_ref=='${var.github_repository}/${each.value.workflow_path}@refs/heads/${var.github_branch}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com/"
  }
}

resource "google_service_account_iam_member" "github_federation" {
  for_each = local.github_identities

  service_account_id = google_service_account.environment[each.value.service_account].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/projects/${data.google_project.current.number}/locations/global/workloadIdentityPools/${google_iam_workload_identity_pool.github[each.key].workload_identity_pool_id}/*"
}

locals {
  terraform_project_roles = toset([
    "roles/artifactregistry.admin",
    "roles/cloudscheduler.admin",
    "roles/cloudtasks.admin",
    "roles/iam.roleViewer",
    "roles/iam.securityReviewer",
    "roles/iam.serviceAccountViewer",
    "roles/iam.workloadIdentityPoolViewer",
    "roles/run.admin",
    "roles/serviceusage.serviceUsageAdmin",
  ])

  deployer_project_roles = toset([
    "roles/artifactregistry.writer",
    "roles/run.developer",
  ])
}

resource "google_project_iam_member" "terraform" {
  for_each = local.terraform_project_roles

  project = var.project_id
  role    = each.value
  member  = google_service_account.environment["terraform"].member
}

# Secret payload and IAM mutation are intentionally excluded. Updating this
# custom role or any IAM/WIF/service account binding is a local-admin bootstrap
# operation, not a routine GitHub Terraform identity capability.
resource "google_project_iam_custom_role" "secret_container_manager" {
  project     = var.project_id
  role_id     = "${title(var.environment)}SecretContainerManager"
  title       = "${title(var.environment)} Secret Container Manager"
  description = "Manage secret containers and inspect metadata without payload or IAM access."

  permissions = [
    "resourcemanager.projects.get",
    "secretmanager.locations.get",
    "secretmanager.locations.list",
    "secretmanager.secrets.create",
    "secretmanager.secrets.delete",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.update",
    "secretmanager.versions.get",
    "secretmanager.versions.list",
  ]

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_iam_member" "terraform_secret_container_manager" {
  project = var.project_id
  role    = google_project_iam_custom_role.secret_container_manager.name
  member  = google_service_account.environment["terraform"].member
}

resource "google_project_iam_member" "deployer" {
  for_each = local.deployer_project_roles

  project = var.project_id
  role    = each.value
  member  = google_service_account.environment["deployer"].member
}

resource "google_storage_bucket_iam_member" "terraform_state" {
  bucket = var.state_bucket_name
  role   = "roles/storage.objectAdmin"
  member = google_service_account.environment["terraform"].member
}

resource "google_service_account_iam_member" "deployer_act_as" {
  for_each = toset(["api_runtime", "worker_runtime", "dispatcher_runtime"])

  service_account_id = google_service_account.environment[each.value].name
  role               = "roles/iam.serviceAccountUser"
  member             = google_service_account.environment["deployer"].member
}

resource "google_service_account_iam_member" "terraform_act_as_runtime" {
  for_each = toset(["api_runtime", "worker_runtime", "dispatcher_runtime", "scheduler_invoker"])

  service_account_id = google_service_account.environment[each.value].name
  role               = "roles/iam.serviceAccountUser"
  member             = google_service_account.environment["terraform"].member
}

resource "google_service_account_iam_member" "dispatcher_act_as_tasks_invoker" {
  service_account_id = google_service_account.environment["tasks_invoker"].name
  role               = "roles/iam.serviceAccountUser"
  member             = google_service_account.environment["dispatcher_runtime"].member
}

resource "google_cloud_tasks_queue" "mission_generation" {
  project  = var.project_id
  location = var.region
  name     = local.resource_names.queue

  rate_limits {
    max_concurrent_dispatches = 1
    max_dispatches_per_second = 1
  }

  retry_config {
    max_attempts  = 5
    min_backoff   = "10s"
    max_backoff   = "300s"
    max_doublings = 16
  }

  depends_on = [google_project_service.required]
}

resource "google_cloud_tasks_queue_iam_member" "dispatcher_enqueuer" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_tasks_queue.mission_generation.name
  role     = "roles/cloudtasks.enqueuer"
  member   = google_service_account.environment["dispatcher_runtime"].member
}
