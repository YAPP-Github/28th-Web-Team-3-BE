# API E2E load tests

실제 서버 API에서 시작하는 20명 기준 k6 E2E 부하 테스트다. 모든 사용자는 고유한 게스트 UUID로 시작하며 테스트 간 사용자 상태를 재사용하지 않는다.

## Scenarios

| 파일 | 부하 모델 | 측정 범위 |
| --- | --- | --- |
| `full-flow.js` | 10초 동안 초당 2명, 총 20회 | 게스트 인증 → 온보딩 → 목표 → 콘텐츠 → 미션 생성·확정·완료 |
| `no-mission-flow.js` | 10초 동안 초당 2명, 총 20회 | 게스트 인증 → 온보딩 → 목표 → 콘텐츠 |
| `mission-only.js` | 준비된 20명이 각 1회 동시 실행 | 미션 생성 요청 → 폴링 → 초안 조회 → 미션 확정 |

복합 플로우는 현재 제품 계약인 v2 목표 미리보기·확정 API를 사용한다. 정책과 팁은 목록 조회를 항상 수행하고, 데이터가 존재할 때만 첫 항목의 상세 조회와 북마크를 수행한다.

미션 전용 시나리오의 `setup()`은 서버 API로 게스트 20명을 발급하고 온보딩을 완료한다. 이 준비 요청에는 `phase=setup` 태그가 붙으며 측정 구간 HTTP 임계값에서 제외된다. 미션 입력은 `DELIVERY_FOOD`, `HOUSEHOLD_GOODS`, `CLASS` 세 항목을 순환한다.

## Prerequisites and safety

- k6가 설치되어 있어야 한다.
- 로컬 기본 대상은 `http://localhost:8080`이다.
- 로컬이 아닌 서버는 `ALLOW_NON_LOCAL_LOAD=true`를 명시하지 않으면 실행이 중단된다.
- 실제 운영 환경이 아니라 삭제 가능한 부하 테스트 환경에서 실행한다.
- 테스트가 만든 게스트·온보딩·목표·북마크·미션 데이터는 자동 삭제하지 않는다. 실행 후 환경 단위로 정리하거나 테스트 DB를 초기화한다.
- 토큰, 비밀번호, 관리자 키는 스크립트나 저장소에 기록하지 않는다.

## Run

로컬 서버:

```bash
k6 run scripts/k6/e2e/no-mission-flow.js
k6 run scripts/k6/e2e/full-flow.js
k6 run scripts/k6/e2e/mission-only.js
```

원격 테스트 서버:

```bash
k6 run \
  -e BASE_URL=https://load-test.example.com \
  -e ALLOW_NON_LOCAL_LOAD=true \
  scripts/k6/e2e/full-flow.js
```

권장 실행 순서는 다음과 같다.

1. `no-mission-flow.js`로 일반 API와 DB 기준선을 확보한다.
2. 시스템이 안정된 뒤 `full-flow.js`를 실행하여 미션 포함 결과와 비교한다.
3. 다시 안정된 뒤 `mission-only.js`로 미션 큐와 Worker에 20명 동시 부하를 준다.

실행 사이에는 큐 깊이, Worker 처리 중 작업, DB 연결 수가 정상 수준으로 돌아왔는지 확인한다.

## Configuration

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | API 서버 루트 URL |
| `ALLOW_NON_LOCAL_LOAD` | `false` | 원격 서버 부하 주입 명시 승인 |
| `USER_COUNT` | `20` | 사전 할당 VU 및 미션 전용 사용자·반복 수 |
| `FLOW_RATE` | `2` | 복합 플로우에서 초당 시작할 사용자 수 |
| `FLOW_DURATION` | `10s` | 복합 플로우 사용자 투입 시간 |
| `MISSION_TIMEOUT_MS` | `30000` | 개별 미션 Job 폴링 최대 대기 시간 |
| `THINK_TIME_ENABLED` | `true` | 온보딩 화면 사이 0.3~1.0초 think time 적용 여부 |

복합 플로우의 총 시작 횟수는 `FLOW_RATE × FLOW_DURATION`이다. 기본값은 `2 × 10초 = 20명`이다. `USER_COUNT`는 이 실행들을 처리할 VU 상한이므로 총 사용자 수를 바꿀 때는 세 값을 함께 조정한다.

`MISSION_TIMEOUT_MS`를 진단 목적으로 늘리더라도 성능 합격 기준인 `mission_generation_duration p95 < 30000ms`는 바뀌지 않는다.

## Pass criteria

| 지표 | 기준 |
| --- | --- |
| 측정 구간 HTTP 실패율 | `< 1%` |
| 측정 구간 HTTP 응답시간 | `p95 < 500ms` |
| 측정 구간 check 성공률 | `> 99%` |
| 사용자 플로우 실패율 | `0%` |
| dropped iterations | `0` |
| 미션 Job 실패율 | `0%` |
| 미션 생성 시간 | `p95 < 30초` |

`mission_generation_duration`은 생성 Job `POST` 직전부터 상태가 `SUCCEEDED`가 될 때까지다. `mission_creation_duration`은 초안 조회와 확정까지 포함한다. 비동기 생성 성능은 빠른 `202 Accepted` 응답이 아니라 `mission_generation_duration`으로 판단한다.

서버 측에서는 함께 다음을 확인한다.

- Cloud Tasks 큐 깊이와 oldest task age
- Worker 인스턴스 수와 동시 처리량
- DB connection pool, CPU, lock 및 slow query
- AI 호출 rate limit, retry 횟수와 실패 코드
