resource "google_cloud_run_v2_service" "api" {
  count = var.enable_runtime_services ? 1 : 0

  project             = var.project_id
  location            = var.region
  name                = local.resource_names.api
  deletion_protection = var.deletion_protection
  ingress             = "INGRESS_TRAFFIC_ALL"
  labels              = local.labels

  scaling {
    min_instance_count = var.api_min_instances
  }

  template {
    service_account = google_service_account.environment["api_runtime"].email
    timeout         = "300s"

    containers {
      image = var.bootstrap_image_uri

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      dynamic "env" {
        for_each = merge(local.common_environment_variables, {
          APP_ROLE                            = "api"
          AI_ACTIVATION                       = "off"
          MISSION_GENERATION_DELIVERY_ENABLED = "false"
        })
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = local.database_secret_environment
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.runtime[env.value].secret_id
              version = "latest"
            }
          }
        }
      }

      dynamic "env" {
        for_each = var.enable_policy_import ? {
          POLICY_IMPORT_TOKEN  = "policy_import_token"
          YOUTH_POLICY_API_KEY = "youth_policy_api_key"
        } : {}
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.runtime[env.value].secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]

    precondition {
      condition     = var.bootstrap_image_uri != null && trimspace(var.bootstrap_image_uri) != ""
      error_message = "bootstrap_image_uri is required when enable_runtime_services is true."
    }
  }

  depends_on = [
    google_project_service.required,
    google_secret_manager_secret_iam_member.runtime_accessor,
  ]
}

resource "google_cloud_run_v2_service" "worker" {
  count = var.enable_runtime_services ? 1 : 0

  project             = var.project_id
  location            = var.region
  name                = local.resource_names.worker
  deletion_protection = var.deletion_protection
  ingress             = "INGRESS_TRAFFIC_ALL"
  labels              = local.labels

  scaling {
    min_instance_count = var.background_min_instances
  }

  template {
    service_account                  = google_service_account.environment["worker_runtime"].email
    timeout                          = "600s"
    max_instance_request_concurrency = 1

    containers {
      image = var.bootstrap_image_uri

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      dynamic "env" {
        for_each = merge(local.common_environment_variables, {
          APP_ROLE                            = "mission-worker"
          AI_ACTIVATION                       = var.enable_ai ? "on" : "off"
          MISSION_LIFECYCLE_SCHEDULER_ENABLED = "false"
          NAVER_BLOG_AI_CONTEXT_COUNT         = tostring(var.naver_blog_ai_context_count)
        })
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = local.database_secret_environment
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.runtime[env.value].secret_id
              version = "latest"
            }
          }
        }
      }

      dynamic "env" {
        for_each = var.enable_ai ? local.ai_secret_environment : {}
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.runtime[env.value].secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]

    precondition {
      condition     = var.bootstrap_image_uri != null && trimspace(var.bootstrap_image_uri) != ""
      error_message = "bootstrap_image_uri is required when enable_runtime_services is true."
    }
  }

  depends_on = [
    google_project_service.required,
    google_secret_manager_secret_iam_member.runtime_accessor,
  ]
}

resource "google_cloud_run_v2_service" "dispatcher" {
  count = var.enable_runtime_services ? 1 : 0

  project             = var.project_id
  location            = var.region
  name                = local.resource_names.dispatcher
  deletion_protection = var.deletion_protection
  ingress             = "INGRESS_TRAFFIC_ALL"
  labels              = local.labels

  scaling {
    min_instance_count = var.background_min_instances
  }

  template {
    service_account                  = google_service_account.environment["dispatcher_runtime"].email
    timeout                          = "300s"
    max_instance_request_concurrency = 1

    containers {
      image = var.bootstrap_image_uri

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }

      dynamic "env" {
        for_each = merge(local.common_environment_variables, {
          APP_ROLE                                      = "mission-dispatcher"
          AI_ACTIVATION                                 = "off"
          MISSION_LIFECYCLE_SCHEDULER_ENABLED           = "false"
          MISSION_GENERATION_DELIVERY_ENABLED           = "true"
          MISSION_GENERATION_DELIVERY_PROJECT_ID        = var.project_id
          MISSION_GENERATION_DELIVERY_LOCATION          = var.region
          MISSION_GENERATION_DELIVERY_QUEUE             = google_cloud_tasks_queue.mission_generation.name
          MISSION_GENERATION_WORKER_URL                 = google_cloud_run_v2_service.worker[0].uri
          MISSION_GENERATION_TASKS_OIDC_SERVICE_ACCOUNT = google_service_account.environment["tasks_invoker"].email
        })
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = local.database_secret_environment
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.runtime[env.value].secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]

    precondition {
      condition     = var.bootstrap_image_uri != null && trimspace(var.bootstrap_image_uri) != ""
      error_message = "bootstrap_image_uri is required when enable_runtime_services is true."
    }
  }

  depends_on = [
    google_project_service.required,
    google_secret_manager_secret_iam_member.runtime_accessor,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "api_public" {
  count = var.enable_runtime_services ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.api[0].name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service_iam_member" "tasks_invoke_worker" {
  count = var.enable_runtime_services ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.worker[0].name
  role     = "roles/run.invoker"
  member   = google_service_account.environment["tasks_invoker"].member
}

resource "google_cloud_run_v2_service_iam_member" "scheduler_invoke_dispatcher" {
  count = var.enable_runtime_services ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.dispatcher[0].name
  role     = "roles/run.invoker"
  member   = google_service_account.environment["scheduler_invoker"].member
}

resource "google_cloud_scheduler_job" "mission_dispatch" {
  count = var.enable_runtime_services ? 1 : 0

  project          = var.project_id
  region           = var.region
  name             = local.resource_names.scheduler
  description      = "Dispatches ${var.environment} mission generation jobs."
  schedule         = var.scheduler_schedule
  time_zone        = var.scheduler_time_zone
  attempt_deadline = "180s"
  paused           = var.scheduler_paused

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.dispatcher[0].uri}/internal/mission-generation/dispatch"

    oidc_token {
      service_account_email = google_service_account.environment["scheduler_invoker"].email
      audience              = google_cloud_run_v2_service.dispatcher[0].uri
    }
  }

  depends_on = [
    google_cloud_run_v2_service_iam_member.scheduler_invoke_dispatcher,
    google_project_service.required,
  ]
}
