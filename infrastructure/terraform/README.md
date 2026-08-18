# Terraform 운영 가이드

## 원칙

- Prod와 Dev는 별도 GCP 프로젝트, root module, remote state, service account, WIF, Secret Manager를 사용한다.
- 모든 신규 리소스는 `prod-` 또는 `dev-` prefix를 사용한다.
- Terraform은 secret 리소스와 IAM만 관리하며 실제 secret value/version은 관리하지 않는다.
- GitHub Environment Secrets를 수동 workflow로 GCP Secret Manager에 동기화한다.
- 기존 unprefixed 리소스는 prefixed 환경 검증과 client cutover가 끝나기 전 삭제하지 않는다.
- Prod apply와 배포는 `main`에서 수동 실행한다. Dev Terraform apply는 `dev`에서 수동 실행하고, Dev application deploy는 runtime 서비스 준비 완료 후 `dev` push마다 자동 실행한다.

## 활성화 전 사용자 필수 게이트

현재 GitHub API 확인 기준 `dev`, `prod` Environment에는 protection rule/deployment branch policy가 없고 `main`, `dev`에도 branch protection이 없다. 아래가 끝나기 전에는 Terraform apply, secret sync, 신규 서비스 배포를 실행하지 않는다.

1. `prod` Environment에 required reviewer와 `main` deployment branch policy를 설정한다.
2. `dev` Environment에 승인자 정책과 `dev` deployment branch policy를 설정한다.
3. `main`, `dev`에 force-push/delete 금지와 필요한 PR/status-check ruleset을 설정한다.
4. Dev 전용 DB를 만들고 Prod와 다른 endpoint/user/password인지 확인한 후에만 `DATABASE_ISOLATION_CONFIRMED=true`를 입력한다.
5. 환경별 state bucket과 아래 GitHub Variables/Secrets를 입력한다.
6. `dev`의 `RUNTIME_SERVICES_READY`는 최초 `false`로 입력하고, 아래 Bootstrap 및 smoke test가 완료된 뒤에만 `true`로 변경한다.
7. 기존 stack rollback 기준 commit `43da4f595ccc79f488a6261fcd230c20d422b50c`에 보호된 tag를 만들고 현재 known-good image digest/API URL/Scheduler 상태를 비공개 운영 기록에 남긴다.

예시 tag 이름은 `legacy-before-terraform-20260816`이다. tag 생성·push와 tag ruleset 적용은 cutover 승인자가 직접 수행한다.

## 디렉터리

```text
infrastructure/terraform/
├── modules/backend-environment/
└── environments/
    ├── dev/
    └── prod/
```

각 환경 디렉터리는 별도 GCS backend를 사용한다. HashiCorp workspace 하나로 Prod/Dev를 전환하지 않는다.

## Terraform 관리 범위

- 필요한 GCP API
- prefixed Artifact Registry repository
- Terraform/app deploy/secret sync/runtime/invoker service accounts
- 환경 및 branch 제한 WIF Pool/Provider
- Secret Manager secret containers와 secret별 최소 권한 IAM
- Cloud Tasks queue와 retry/rate 설정
- Cloud Run API/worker/dispatcher와 service-level scaling
- Cloud Scheduler job과 OIDC 호출

Cloud Run 이미지 변경은 배포 workflow가 소유한다. Terraform은 최초 생성 시 `TF_BOOTSTRAP_IMAGE_URI`를 사용한 뒤 image drift를 무시한다.

## Scaling

| Environment | API | Worker | Dispatcher |
| --- | ---: | ---: | ---: |
| Prod | 1 | 0 | 0 |
| Dev | 0 | 0 | 0 |

값은 Cloud Run service-level scaling으로 관리하며 revision-level min instances는 사용하지 않는다.

## GitHub Environment Variables

`dev`, `prod` Environment 각각에 값을 입력한다. 실제 값은 이 문서나 이슈 댓글에 기록하지 않는다.

### Terraform

- `GCP_PROJECT_ID`
- `GCP_REGION`
- `TF_STATE_BUCKET`
- `TF_STATE_PREFIX`
- `TF_BOOTSTRAP_IMAGE_URI`
- `TF_ENABLE_RUNTIME_SERVICES`: 최초 `false`, secret 동기화 후 `true`
- `RUNTIME_SERVICES_READY`: Dev는 최초 `false`; `dev-*` runtime 서비스 생성 및 smoke test 뒤에만 `true`
- `TERRAFORM_SERVICE_ACCOUNT`
- `SECRET_SYNC_SERVICE_ACCOUNT`
- `TERRAFORM_WORKLOAD_IDENTITY_PROVIDER`
- `DEPLOY_WORKLOAD_IDENTITY_PROVIDER`
- `SECRET_SYNC_WORKLOAD_IDENTITY_PROVIDER`
- `SCHEDULER_PAUSED`: cutover 승인 전 `true`
- `DATABASE_ISOLATION_CONFIRMED`: Dev 전용 DB endpoint/user/password 검증 후에만 Dev에서 `true`

### Application configuration

- `SERVER_URL`
- `JWT_ISSUER`
- `JWT_AUDIENCE`
- `JWT_ACCESS_TOKEN_TTL`
- `JWT_REFRESH_TOKEN_TTL`
- `NAVER_BLOG_AI_CONTEXT_COUNT`
- `AI_ACTIVATION`: Dev 초기값 `off`, Prod `on`
- `POLICY_IMPORT_ENABLED`: Dev 초기값 `false`, Prod `true`

### Application deployment resource names

- `ARTIFACT_REGISTRY_REPOSITORY`
- `CLOUD_RUN_SERVICE`
- `DEPLOY_SERVICE_ACCOUNT`
- `MISSION_WORKER_SERVICE`
- `MISSION_DISPATCHER_SERVICE`

Terraform output의 `resource_names`, `service_accounts`, `workload_identity_providers`를 사용해 입력한다. 배포 workflow는 이미지 변경만 담당하며 runtime 설정, IAM, secret reference, scaling은 Terraform만 소유한다.

## GitHub Environment Secrets

`dev`, `prod`에 각각 별도 값으로 입력한다.

Secret Manager의 secret ID는 기존 대문자 환경변수 이름을 그대로 사용한다. 환경별 GCP 프로젝트가 분리되어 있으므로 secret ID에 `dev-`/`prod-` 접두사는 붙이지 않는다.

필수:

- `SPRING_DATABASE_URL`
- `SPRING_DATABASE_USER`
- `SPRING_DATABASE_PASSWORD`
- `JWT_SECRET`

선택 기능 활성화 시 필수:

- `GOOGLE_GENAI_API_KEY`
- `NAVER_BLOG_CLIENT_ID`
- `NAVER_BLOG_CLIENT_SECRET`
- `POLICY_IMPORT_TOKEN`
- `YOUTH_POLICY_API_KEY`

Dev DB 세 값은 Prod와 다른 데이터베이스를 가리켜야 한다. 같은 값이면 Dev runtime을 활성화하지 않는다.

## Secret 흐름

```text
GitHub Environment Secrets
        │ manual workflow_dispatch
        ▼
Sync GCP Secrets workflow
        │ adds a new version; never uses Terraform variables
        ▼
Environment-specific GCP Secret Manager
        │ runtime reference
        ▼
Cloud Run
```

GitHub Secrets가 입력 원본이지만 Cloud Run은 GitHub에 직접 접근하지 않는다. 런타임 저장소는 계속 GCP Secret Manager다.

## 최초 Bootstrap

Terraform backend bucket과 최초 Terraform 실행 identity에는 bootstrap 문제가 있으므로 첫 foundation apply는 관리자가 로컬 ADC로 수행한다.

1. `dev-`, `prod-` prefix의 환경별 전용 GCS state bucket을 생성하고 versioning, uniform bucket-level access, public access prevention, retention/recovery 정책과 감사 로그를 활성화한다.
2. 환경 디렉터리에서 backend를 초기화한다.
3. 실제 값은 shell environment의 `TF_VAR_*`와 `-backend-config`로 전달한다.
4. `state_bucket_name`을 전달하고 `enable_runtime_services=false`로 foundation apply한다. 이 apply가 WIF, service account, IAM과 환경별 Terraform SA의 bucket object 권한을 만든다.
5. Terraform output을 GitHub Environment Variables에 직접 입력한다.
6. 기존 Secret Manager secret이 있다면 Terraform state에 import한다. dev의 기존 DB secret은 다음 resource address로 import한다.

   ```bash
   terraform import 'module.backend.google_secret_manager_secret.runtime["spring_database_url"]' \
     'projects/<dev-project-id>/secrets/SPRING_DATABASE_URL'
   terraform import 'module.backend.google_secret_manager_secret.runtime["spring_database_user"]' \
     'projects/<dev-project-id>/secrets/SPRING_DATABASE_USER'
   terraform import 'module.backend.google_secret_manager_secret.runtime["spring_database_password"]' \
     'projects/<dev-project-id>/secrets/SPRING_DATABASE_PASSWORD'
   ```

7. GitHub Environment Secrets를 입력하고 `Sync GCP Secrets` workflow를 실행한다.
8. 환경 전용 bootstrap image URI와 현재 정상 API URL을 `SERVER_URL`에 등록한다.
9. `TF_ENABLE_RUNTIME_SERVICES=true`로 변경한 뒤 Terraform apply한다.
10. 신규 API URI를 `SERVER_URL`에 반영하고 다시 plan/apply한다.
11. Dev smoke test가 성공하면 `RUNTIME_SERVICES_READY=true`로 변경해 이후 `dev` push 자동 배포를 활성화한다. Scheduler pause 해제와 client endpoint 전환은 별도 승인한다.

GitHub의 routine Terraform identity에는 Project IAM Admin, service account/WIF admin, secret IAM 변경 권한을 부여하지 않는다. IAM/WIF/service account 또는 custom role 변경은 항상 로컬 bootstrap 관리자 plan/apply로 수행한다. GitHub Terraform workflow는 foundation resource(Artifact Registry, API enablement, service account, WIF, Secret Manager/IAM) 변경이 포함된 apply를 명시적으로 거부하며, runtime 인프라 변경과 drift 확인만 담당한다.

예시 명령은 placeholder만 사용한다.

```bash
cd infrastructure/terraform/environments/dev
terraform init \
  -backend-config="bucket=<dev-state-bucket>" \
  -backend-config="prefix=backend/dev"
terraform plan
```

## 수동 Workflow

- `Terraform`: `apply` 실행도 먼저 plan job을 완료하고, 그 binary plan을 1일 보존 artifact로 고정한다. Job Summary의 resource/action 목록을 검토한 뒤 별도 apply job의 Prod Environment 승인을 통과해야 같은 artifact가 적용된다. 삭제/교체는 별도 체크박스 없이는 plan 단계에서 거부한다.
- `Sync GCP Secrets`: GitHub Environment Secrets를 동일 환경 GCP Secret Manager의 새 version으로 동기화한다.
- `Dev CI/CD`: PR은 CI만 수행한다. `dev` push와 `workflow_dispatch`는 `RUNTIME_SERVICES_READY=true`인 경우에만 자동/수동 배포를 수행한다.
- `Prod CI/CD`: Prod deploy는 `main`의 `workflow_dispatch`에서만 수행한다.

모든 apply/deploy concurrency는 환경별로 직렬화하고 수동 실행은 중간 취소하지 않는다.

## Rollback

1. 새 Scheduler는 `SCHEDULER_PAUSED=true`로 plan/apply하고 기존 Scheduler 상태를 유지한다.
2. 앱 endpoint/DNS를 변경 전 기록한 기존 unprefixed API URL로 되돌린다.
3. 신규 prefixed 서비스는 이전 정상 image digest로 이미지 전용 수동 배포를 실행한다.
4. 잘못 동기화한 secret은 GCP Secret Manager에서 직전 기록된 version을 확인해 새 version으로 재등록하고, 문제 version은 검증 후 disable한다.
5. 기존 unprefixed stack은 이 전환 workflow가 소유하지 않으므로 기존 배포 workflow/tag를 별도 보존한다. 신규 script에 기존 리소스 변수를 넣어 복구하지 않는다.
6. prefixed 리소스와 state는 원인 분석 전 삭제하지 않는다.

## 금지 사항

- 실제 secret value를 `.tf`, `.tfvars`, workflow input, issue, PR body에 기록
- `google_secret_manager_secret_version.secret_data` 사용
- Terraform plan을 공개·장기 artifact로 업로드 (동일 run의 별도 승인 apply job에만 전달되는 1일 보존 reviewed-plan artifact는 예외)
- Dev에서 Prod DB/project/service account/secret/queue URL 참조
- Prod에서 Dev 리소스 참조
- 검증 전 기존 리소스 삭제
