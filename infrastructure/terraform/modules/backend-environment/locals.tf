locals {
  prefix = var.environment

  resource_names = {
    artifact_repository = "${local.prefix}-yapp-team3"
    api                 = "${local.prefix}-yapp-backend"
    worker              = "${local.prefix}-mission-generation-worker"
    dispatcher          = "${local.prefix}-mission-generation-dispatcher"
    queue               = "${local.prefix}-mission-generation"
    scheduler           = "${local.prefix}-mission-generation-dispatch"
  }

  github_identities = {
    terraform = {
      pool_id         = "${local.prefix}-gh-terraform"
      provider_id     = "${local.prefix}-gh-terraform"
      service_account = "terraform"
      workflow_path   = ".github/workflows/terraform.yml"
    }
    deployer = {
      pool_id         = "${local.prefix}-gh-deployer"
      provider_id     = "${local.prefix}-gh-deployer"
      service_account = "deployer"
      workflow_path   = var.environment == "prod" ? ".github/workflows/prod-cicd.yml" : ".github/workflows/dev-cicd.yml"
    }
    secret_sync = {
      pool_id         = "${local.prefix}-gh-secret-sync"
      provider_id     = "${local.prefix}-gh-secret-sync"
      service_account = "secret_sync"
      workflow_path   = ".github/workflows/sync-gcp-secrets.yml"
    }
  }

  service_account_ids = {
    terraform          = "${local.prefix}-terraform-admin"
    deployer           = "${local.prefix}-deployer"
    secret_sync        = "${local.prefix}-secret-sync"
    api_runtime        = "${local.prefix}-api-runtime"
    worker_runtime     = "${local.prefix}-mission-worker-runtime"
    dispatcher_runtime = "${local.prefix}-mission-dispatch-runtime"
    tasks_invoker      = "${local.prefix}-mission-tasks-invoker"
    scheduler_invoker  = "${local.prefix}-mission-scheduler-inv"
  }

  secret_ids = {
    spring_database_url      = "SPRING_DATABASE_URL"
    spring_database_user     = "SPRING_DATABASE_USER"
    spring_database_password = "SPRING_DATABASE_PASSWORD"
    jwt_secret               = "JWT_SECRET"
    google_genai_api_key     = "GOOGLE_GENAI_API_KEY"
    naver_blog_client_id     = "NAVER_BLOG_CLIENT_ID"
    naver_blog_client_secret = "NAVER_BLOG_CLIENT_SECRET"
    policy_import_token      = "POLICY_IMPORT_TOKEN"
    youth_policy_api_key     = "YOUTH_POLICY_API_KEY"
  }

  required_apis = toset([
    "artifactregistry.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "cloudscheduler.googleapis.com",
    "cloudtasks.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "storage.googleapis.com",
    "sts.googleapis.com",
  ])

  common_environment_variables = {
    SPRING_PROFILES_ACTIVE = var.environment
    SERVER_URL             = var.server_url
    JWT_ISSUER             = var.jwt_issuer
    JWT_AUDIENCE           = var.jwt_audience
    JWT_ACCESS_TOKEN_TTL   = var.jwt_access_token_ttl
    JWT_REFRESH_TOKEN_TTL  = var.jwt_refresh_token_ttl
  }

  database_secret_environment = {
    SPRING_DATABASE_URL      = "spring_database_url"
    SPRING_DATABASE_USERNAME = "spring_database_user"
    SPRING_DATABASE_PASSWORD = "spring_database_password"
    JWT_SECRET               = "jwt_secret"
  }

  ai_secret_environment = {
    GOOGLE_GENAI_API_KEY     = "google_genai_api_key"
    NAVER_BLOG_CLIENT_ID     = "naver_blog_client_id"
    NAVER_BLOG_CLIENT_SECRET = "naver_blog_client_secret"
  }

  labels = {
    application = "yapp-backend"
    environment = var.environment
    managed-by  = "terraform"
  }
}
