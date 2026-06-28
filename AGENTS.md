# 28th-Web-Team-3-BE AI Agent Harness

이 문서는 `28th-Web-Team-3-BE` 프로젝트에서 AI Agent가 작업할 때 따라야 하는 프로젝트 로컬 하네스다.
사용자의 요청에 따라 계속 수정될 수 있도록 각 영역을 분리해서 관리한다.

## Instruction Priority

AI Agent는 아래 우선순위를 따른다.

1. 현재 사용자 직접 지시
2. 팀 공통 하네스: [`YAPP-Github/28th-Web-Team-3-Harness`](https://github.com/YAPP-Github/28th-Web-Team-3-Harness) 저장소의 `28th-Web-Team-3-Harness` 디렉터리
3. 현재 백엔드 프로젝트 하네스: 이 파일, `28th-Web-Team-3-BE/AGENTS.md`
4. 현재 백엔드 프로젝트 내부 문서와 코드 규칙
5. 개인 AI Agent 하네스, 개인 Codex skills, 기타 보조 규칙

팀 공통 하네스는 아직 작성되지 않았더라도, 나중에 생성되면 그 시점부터 이 파일보다 높은 우선순위로 적용한다.
상위 지침과 하위 지침이 충돌하면 상위 지침을 따른다.
충돌이 작업에 영향을 주면 Agent는 사용자에게 짧게 보고하고, 임의로 하위 지침을 우회하지 않는다.

## Project Stack

- Language: Kotlin 2.3.20
- JDK: 25
- Framework: Spring Boot 4.0.6
- Build Tool: Gradle
- CI/CD: GitHub Actions

## Git And Branch Convention

### Branch Strategy

- Production branch: `main`
- Development branch: `dev`
- Feature and task branches use this format:

```text
<prefix>/<issue-number>-<english-feature-name>
```

Examples:

```text
feat/1-english
feat/3-new-feature
fix/12-login-error
```

The branch prefix must follow the commit type list below.
The feature name after the issue number must be written in English.

### Commit Types

Allowed commit and branch prefixes:

- `feat`: 새로운 기능 개발
- `fix`: 오류, 버그, 문제가 있는 코드 수정
- `chore`: Gradle 파일, Agent 파일 등 개발 코드는 아니지만 실행이나 작업 환경에 영향이 있는 파일 수정
- `docs`: Swagger 등 실행 코드에 영향이 없는 문서화 작업
- `test`: 테스트 코드 작성 또는 수정
- `ci`: GitHub Actions 등 CI/CD 파이프라인 스크립트 작성 또는 수정
- `flyway`: Flyway SQL 문서 작성 또는 수정

### Commit Message Format

Commit messages use only a title and do not include a body.

Format:

```text
<type> : <title>
```

Example:

```text
feat : 회원가입 API 추가
```

The title should be written in Korean, stay concise, and focus on the main point.

## Code Convention

Kotlin source code must follow the official Kotlin coding conventions:

https://kotlinlang.org/docs/coding-conventions.html#source-code-organization

When writing or modifying Kotlin code, Agent must treat the official Kotlin convention as the source of truth for formatting, source organization, naming, and style unless a higher-priority team rule overrides it.

## Module Structure

이 프로젝트는 멀티 모듈 Spring Boot 프로젝트로 구성된다.

최상위 모듈:

- `api`
- `core`
- `infra`
- `common`

`api` 모듈만 실행 가능한 Spring Boot jar(`bootJar`)를 생성하며, 나머지 모듈은 일반 라이브러리 jar로 빌드된다.
의존성 방향은 한 방향만 허용한다.

```
api → infra → core → common
api ────────→ core
api ─────────────→ common
infra ───────────→ common
```

역방향 의존성(`common` → `core`, `core` → `infra`, `infra` → `api` 등)은 금지한다.

### `api` Module

`api` 모듈은 Spring Boot 진입점과 컨트롤러, 요청/응답 DTO, HTTP 레벨 예외 처리 등 웹 경계 코드를 담는다.
도메인 비즈니스 로직이나 영속성 관련 코드는 포함하지 않는다.

### `core` Module

`core` 모듈은 도메인 비즈니스 로직, 도메인 모델, 서비스, 영속성 계층(JPA 엔티티와 리포지터리)을 담는다.
외부 시스템과 직접 통신하지 않는다. 외부 연동이 필요하면 `core` 안에 포트(인터페이스)만 정의하고, 구현은 `infra`에서 제공한다.
프로젝트가 커지면 이 영역 아래에 도메인별 서브모듈을 만들 수 있다.
서브모듈 경계는 비즈니스 컨텍스트, 응집도, 결합도를 기준으로 나눈다.

### `infra` Module

`infra` 모듈은 외부 시스템 어댑터를 담는다.
예: JWT 발급/검증, AWS S3 클라이언트, OAuth 통신, 외부 HTTP API 클라이언트, Redis/Kafka 등 외부 SDK 래퍼.
`core`가 정의한 포트 인터페이스를 구현하여 외부 SDK가 도메인 코드로 새어 나오지 않도록 격리하는 것이 목적이다.
`infra`는 `common`과 `core`에만 의존할 수 있다.

### `common` Module

`common` 모듈은 특정 도메인이나 외부 인프라에 결합되지 않은 프로젝트 전역 공통 코드를 담는다.
예: 공통 베이스 타입, 공용 유틸리티, 횡단 관심사 예외, 여러 모듈에서 재사용되는 값 객체 등.
`common` 모듈은 다른 어떤 모듈에도 의존해서는 안 된다.

## Module Boundary Rules

개발자가 새로운 코드 작성이나 리팩터링을 요청할 때, Agent는 요청한 컨텍스트 경계를 존중하되 무비판적으로 적용해서는 안 된다.

모듈 경계를 변경하기 전에 Agent는 해당 변경이 다음에 해당하는지 확인해야 한다.

- 기존 모듈의 책임을 깨뜨리는가
- 모듈 간 불필요한 결합을 만드는가
- 모듈 내부 응집도를 약화시키는가
- 역방향 의존성을 만드는가 (`common` → `core`, `core` → `infra`, `infra` → `api` 등)
- 도메인 비즈니스 로직이나 영속성 코드를 `api`에 배치하는가
- 웹/HTTP 관심사를 `core`, `infra`, `common`에 배치하는가
- 외부 SDK나 외부 시스템 통신 코드를 `infra` 바깥(`core`, `common`)에 배치하는가
- 재사용 가능한 내부 공통 로직을 명확한 이유 없이 특정 기능 전용 모듈에 배치하는가

요청된 경계가 위 규칙을 위반할 우려가 있다면, Agent는 구현을 멈추고 우려 사항을 설명한 뒤 더 나은 방향을 제안하고 사용자에게 확인을 받아야 한다.
최종 구현은 사용자가 수용한 모범 방향을 따른다.

## Secrets And Environment Variables

Environment variable source values are managed outside this repository in the team's separate Notion workspace.

Repository rules:

- Do not commit real secrets, credentials, tokens, passwords, or private keys.
- Use GitHub Actions Secrets for CI/CD injection.
- Local examples may be documented with placeholder values only.
- If environment variables are required for local development, prefer documenting the variable names and expected shape without exposing real values.

## CI/CD

CI/CD is managed with GitHub Actions.

Agent should use the `ci` commit type for changes to GitHub Actions workflows, pipeline scripts, deployment automation, or CI/CD configuration.

## Agent Operating Rules

- Follow the instruction priority defined in this file before editing code.
- Prefer small, scoped changes that match the existing project structure.
- Before claiming that a build, test, or behavior works, run the relevant verification command and report the result.
- Do not introduce secrets into the repository.
- Do not weaken team rules, branch rules, code conventions, or module boundaries without explicit user direction.
