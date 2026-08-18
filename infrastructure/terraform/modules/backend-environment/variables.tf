variable "environment" {
  description = "Deployment environment."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be dev or prod."
  }
}

variable "project_id" {
  description = "GCP project ID dedicated to this environment."
  type        = string
}

variable "region" {
  description = "GCP region for regional resources."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to federate into this environment."
  type        = string
  default     = "YAPP-Github/28th-Web-Team-3-BE"
}

variable "github_repository_owner_id" {
  description = "Immutable numeric GitHub organization ID used in OIDC trust conditions."
  type        = string
  default     = "101037471"
}

variable "github_branch" {
  description = "Only this branch may obtain deployment credentials."
  type        = string
}

variable "state_bucket_name" {
  description = "Pre-created environment-specific GCS bucket used by this root module backend."
  type        = string

  validation {
    condition     = startswith(var.state_bucket_name, "${var.environment}-")
    error_message = "state_bucket_name must start with the environment prefix."
  }
}

variable "bootstrap_image_uri" {
  description = "Existing environment-local image used only to create the first Cloud Run revision."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.bootstrap_image_uri == null ||
      startswith(var.bootstrap_image_uri, "${var.region}-docker.pkg.dev/${var.project_id}/${var.environment}-")
    )
    error_message = "bootstrap_image_uri must use the selected project, region, and an environment-prefixed repository."
  }
}

variable "enable_runtime_services" {
  description = "Create Cloud Run services and Scheduler after required secret versions exist."
  type        = bool
  default     = false
}

variable "enable_ai" {
  description = "Inject AI and Naver secret references into the mission worker."
  type        = bool
  default     = false
}

variable "enable_policy_import" {
  description = "Inject the policy import token into the public API."
  type        = bool
  default     = false
}

variable "server_url" {
  description = "Environment-specific public API URL. It may be empty during the first isolated bootstrap."
  type        = string
  default     = ""
}

variable "jwt_issuer" {
  description = "Environment-specific JWT issuer."
  type        = string
}

variable "jwt_audience" {
  description = "Environment-specific JWT audience."
  type        = string
}

variable "jwt_access_token_ttl" {
  description = "Access token TTL passed to the application."
  type        = string
  default     = "PT3H"
}

variable "jwt_refresh_token_ttl" {
  description = "Refresh token TTL passed to the application."
  type        = string
  default     = "P14D"
}

variable "api_min_instances" {
  description = "Service-level minimum instances for the public API."
  type        = number
}

variable "background_min_instances" {
  description = "Service-level minimum instances for worker and dispatcher."
  type        = number
  default     = 0
}

variable "scheduler_paused" {
  description = "Keep the prefixed scheduler paused until cutover is approved."
  type        = bool
  default     = true
}

variable "scheduler_schedule" {
  description = "Mission dispatcher cron expression."
  type        = string
  default     = "* * * * *"
}

variable "scheduler_time_zone" {
  description = "Mission dispatcher schedule time zone."
  type        = string
  default     = "Etc/UTC"
}

variable "naver_blog_ai_context_count" {
  description = "Number of Naver Blog contexts supplied to AI."
  type        = number
  default     = 15
}

variable "deletion_protection" {
  description = "Protect prefixed Cloud Run services from deletion."
  type        = bool
  default     = true
}
