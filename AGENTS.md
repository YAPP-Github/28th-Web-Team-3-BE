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

This project is intended to be structured as a multi-module Spring Boot project.

Top-level modules:

- `api`
- `domain`
- `global`
- `infra`

### `api` Module

The `api` module contains web entry points such as controllers and request/response handling.
It should focus on HTTP/API boundaries and should not contain core domain business logic.

### `domain` Module

The `domain` module contains domain business logic.
Domain-specific submodules may be created under this area as the project grows.
Submodule boundaries should be based on business context, cohesion, and coupling.

### `global` Module

The `global` module contains project-wide shared code that is not coupled to external tools or infrastructure.
Use this module for internal shared concerns that are broadly reusable across modules.

### `infra` Module

The `infra` module contains integrations with external systems and tools.
Examples include JWT, AWS, database-specific infrastructure, third-party clients, and external service adapters.

## Module Boundary Rules

When a developer requests new code or refactoring, Agent must respect the requested context boundary, but should not apply it blindly.

Before changing module boundaries, Agent must check whether the change:

- Breaks existing module responsibility
- Creates unnecessary coupling between modules
- Weakens cohesion inside a module
- Places external infrastructure concerns outside `infra`
- Places domain business logic inside `api` or external integration code
- Places reusable internal shared logic in a feature-specific module without clear reason

If the requested boundary appears to violate these rules, Agent must pause before implementation, explain the concern, propose a better direction, and ask the user for confirmation.
The final implementation should follow the best-practice direction accepted by the user.

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
