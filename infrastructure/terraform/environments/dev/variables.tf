variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "state_bucket_name" {
  type = string
}

variable "bootstrap_image_uri" {
  type     = string
  default  = null
  nullable = true
}

variable "enable_runtime_services" {
  type    = bool
  default = false
}

variable "scheduler_paused" {
  type    = bool
  default = true
}

variable "enable_ai" {
  type    = bool
  default = false
}

variable "enable_policy_import" {
  type    = bool
  default = false
}

variable "server_url" {
  type    = string
  default = ""
}

variable "jwt_issuer" {
  type = string
}

variable "jwt_audience" {
  type = string
}
