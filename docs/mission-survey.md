# 미션 생성 사전 설문 질문·응답 명세

## 개요

- 질문 조회: `GET /api/missions/surveys/questions?categories=MEAL&categories=HOBBY`
- 설문 저장·교체: `PUT /api/missions/surveys`
- 저장 설문 조회: `GET /api/missions/surveys`
- 현재 설문 저장 스키마 버전: `V2`
- 카테고리: `MEAL`, `TRANSPORT`, `HOBBY`, `LIVING` 중 중복 없이 1~4개
- 질문 수: 카테고리별 5개, 총 20개
- 주관식 입력: `HOBBY_TYPES`에서 `OTHER`를 선택했을 때의 `hobby.otherHobby` 한 곳

질문과 선택지의 표시 문구는 서버가 내려준다. 클라이언트는 `options[].label`을 표시하고 사용자가 선택한 `options[].code`를 PUT 요청에 전달한다.

## 질문 응답 메타데이터

| 필드 | 의미 |
|---|---|
| `answerType` | `SINGLE_CHOICE`, `MULTI_CHOICE`, `NUMBER`, `KEYED_NUMBER` |
| `minSelections`, `maxSelections` | 단일·복수 선택의 최소·최대 개수 |
| `dependsOnQuestionCode` | 현재 질문이 의존하는 선행 질문 |
| `skipWhenOptionCodes` | 선행 질문에서 해당 코드를 선택하면 현재 질문을 생략 |
| `numericRules` | 선행 선택 코드별 숫자 단위와 최솟값·최댓값 |
| `textRules` | 선택 코드별 추가 주관식 입력 길이 규칙 |
| `exclusiveOptionCodes` | 다른 선택지와 동시에 선택할 수 없는 코드 |
| `conditionalOptionRules` | 선행 응답에 따라 허용되는 선택지를 제한하는 규칙 |
| `impacts` | 미션 생성에서 질문을 사용하는 목적 |

## 공통 응답 타입

| `answerType` | 클라이언트 값 | 입력 방식 |
|---|---|---|
| `SINGLE_CHOICE` | `String` | 제공된 옵션 코드 중 1개 |
| `MULTI_CHOICE` | `List<String>` | 제공된 옵션 코드를 중복 없이 선택 |
| `NUMBER` | `Int?` | 선행 선택값에 따른 범위 내 정수. 조건에 따라 `null` |
| `KEYED_NUMBER` | `List<Object>` | 선행 질문에서 선택한 각 코드와 정수를 한 쌍으로 전달 |

숫자 단위는 `TIMES_PER_WEEK`(주당 횟수), `TIMES_PER_FOUR_WEEKS`(4주당 횟수), `SUBSCRIPTION_COUNT`(구독 개수)를 사용한다. `DAYS_PER_WEEK`는 enum에 정의되어 있지만 현재 질문에서는 사용하지 않는다.

## 식사 `MEAL`

| 순서 | 질문 | 클라이언트 필드 | 방식 | 선택지·범위 | 조건 |
|---:|---|---|---|---|---|
| 1 | `MEAL_TARGET`<br>식사비 중 가장 소비가 큰 부분이 어디인가요? | `meal.target: String` | 단일 선택 | `DELIVERY` 배달 음식<br>`DINING_OUT` 외식<br>`PAID_BEVERAGE` 카페·유료 음료<br>`CONVENIENCE_FOOD` 편의점 식사·간식<br>`DRINKING_GATHERING` 술자리·회식<br>`UNKNOWN` 아직 잘 모르겠어요 | 필수 |
| 2 | `MEAL_FREQUENCY`<br>선택한 항목을 평소 한 주에 몇 번 이용하나요? | `meal.weeklyFrequency: Int?` | 숫자 | `PAID_BEVERAGE`: 주 0~14회<br>그 외 일반 target: 주 0~7회 | `target=UNKNOWN`이면 생략하고 `null` |
| 3 | `MEAL_ALTERNATIVES`<br>식사비를 줄일 때 사용할 수 있는 대안은 무엇인가요? | `meal.alternatives: List<String>` | 복수 선택 1~7개 | `COOK` 직접 요리<br>`PREPARE_MEAL` 도시락·간편식 준비<br>`PREPARE_BEVERAGE` 집이나 직장에서 음료 준비<br>`PICKUP` 배달 대신 포장<br>`USE_FRIDGE_FIRST` 냉장고 음식 먼저 사용<br>`BUY_PLANNED_INGREDIENTS` 계획한 식재료만 구매<br>`NO_ALTERNATIVE` 가능한 대안이 없음 | `NO_ALTERNATIVE` 단독 선택 |
| 4 | `MEAL_REASON`<br>해당 소비가 발생하는 가장 큰 이유는 무엇인가요? | `meal.reason: String?` | 단일 선택 | `TIME_OR_ENERGY` 시간·체력 부족<br>`HABIT` 습관<br>`SOCIAL` 약속·사교 활동<br>`DISCOUNT_OR_NOTIFICATION` 할인·쿠폰·알림<br>`NO_COOKING_OR_STORAGE` 조리·보관 환경 부족<br>`ALTERNATIVE_INCONVENIENT` 다른 대안이 불편함 | `target=UNKNOWN`이면 생략하고 `null` |
| 5 | `MEAL_EXCLUSIONS`<br>식사 미션에서 제외해야 할 상황이 있나요? | `meal.exclusions: List<String>` | 복수 선택 1~6개 | `HEALTH_OR_DIET` 건강·식단상 필요한 식사<br>`FIXED_MEAL` 회사·학교에서 정해진 식사<br>`NO_COOKING_ENVIRONMENT` 조리하기 어려운 환경<br>`UNAVOIDABLE_SCHEDULE` 피하기 어려운 회식·가족 일정<br>`NO_REDUCE_FOOD_AMOUNT` 식사량 자체를 줄이는 미션<br>`NONE` 없음 | `NONE` 단독 선택 |

## 교통 `TRANSPORT`

| 순서 | 질문 | 클라이언트 필드 | 방식 | 선택지·범위 | 조건 |
|---:|---|---|---|---|---|
| 1 | `TRANSPORT_PRIMARY_MODE`<br>평소 이용하는 주된 이동수단은 무엇인가요? | `transport.primaryMode: String` | 단일 선택 | `PUBLIC_TRANSIT` 버스·지하철<br>`TAXI` 택시<br>`CAR` 자가용<br>`WALK_OR_BICYCLE` 도보·자전거<br>`SHARED_MOBILITY` 공유 이동수단<br>`VARIES` 상황에 따라 다름 | 필수 |
| 2 | `TRANSPORT_TARGET`<br>교통비 중 가장 바꾸고 싶은 습관은 무엇인가요? | `transport.target: String` | 단일 선택 | `TAXI` 택시 이용<br>`SHORT_DISTANCE_PAID_MOVE` 가까운 거리의 차량·대중교통 이용<br>`CAR_DRIVING` 자가용 운행<br>`PARKING_OR_TOLL` 주차비·통행료<br>`RUSH_COST` 급하게 이동하면서 생기는 비용<br>`UNKNOWN` 아직 잘 모르겠어요 | 필수 |
| 3 | `TRANSPORT_FREQUENCY`<br>선택한 이동을 평소 한 주에 몇 번 이용하나요? | `transport.weeklyFrequency: Int?` | 숫자 | 일반 target: 주 0~7회 | `target=UNKNOWN`이면 생략하고 `null` |
| 4 | `TRANSPORT_REASON`<br>해당 이동수단을 이용하는 이유는 무엇인가요? | `transport.reason: String` | 단일 선택 | `LATE_NIGHT_OR_SAFETY` 심야·안전<br>`TIME_PRESSURE` 시간 부족·지각 우려<br>`WEATHER` 날씨<br>`LUGGAGE_OR_CARE` 짐·동행자·돌봄<br>`POOR_TRANSIT_CONNECTION` 대중교통 연결이 불편함<br>`WALKING_DIFFICULTY` 도보·자전거 이용이 어려움<br>`CONVENIENCE_OR_HABIT` 편리해서 또는 습관적으로 | `target=UNKNOWN`이어도 필수 |
| 5 | `TRANSPORT_EXCLUSIONS`<br>다른 이동 방식으로 바꾸면 안 되는 상황이 있나요? | `transport.exclusions: List<String>` | 복수 선택 1~6개 | `LATE_NIGHT_DANGER` 심야·치안상 위험<br>`EXTREME_WEATHER` 폭우·폭염·폭설<br>`MOBILITY_CONSTRAINT` 건강·이동상 제약<br>`LUGGAGE_OR_CARE` 짐·아동·가족 돌봄<br>`NO_TRANSIT` 대중교통 미운행 시간·지역<br>`NONE` 없음 | `NONE` 단독 선택 |

## 취미 `HOBBY`

| 순서 | 질문 | 클라이언트 필드 | 방식 | 선택지·범위 | 조건 |
|---:|---|---|---|---|---|
| 1 | `HOBBY_TYPES`<br>평소 즐기는 취미는 무엇인가요? | `hobby.hobbies: List<String>`<br>`hobby.otherHobby: String?` | 복수 선택 1~9개<br>`OTHER`는 추가 주관식 | `READING` 독서<br>`MOVIE_OR_OTT` 영화·OTT<br>`GAME` 게임<br>`EXERCISE` 운동<br>`PERFORMANCE_OR_EXHIBITION` 공연·전시<br>`MUSIC_OR_CREATION` 음악·창작<br>`TRAVEL_OR_OUTDOOR` 여행·야외 활동<br>`GATHERING` 모임<br>`OTHER` 기타 | `OTHER` 포함 시 `otherHobby` 필수<br>trim 후 1~50자<br>`OTHER` 미포함 시 `otherHobby=null` |
| 2 | `HOBBY_SPENDING_TYPES`<br>취미비는 주로 어디에 사용하나요? | `hobby.spendingTypes: List<String>` | 복수 선택 1~2개 | `GOODS` 용품·굿즈<br>`DIGITAL_CONTENT` 게임·디지털 콘텐츠<br>`SUBSCRIPTION` 정기구독<br>`CLASS` 수업·클래스<br>`TICKET` 공연·전시·티켓<br>`GATHERING_FEE` 모임비<br>`RENTAL_OR_SPACE` 장비 대여·공간 이용<br>`DO_NOT_REDUCE` 취미비는 줄이고 싶지 않음 | `DO_NOT_REDUCE` 단독 선택 |
| 3 | `HOBBY_MONTHLY_SPENDING`<br>최근 3개월 월평균 취미비는 어느 정도인가요? | `hobby.monthlySpendingRange: String?` | 단일 선택 | `UNDER_50K` 5만 원 미만<br>`FROM_50K_TO_150K` 5만~15만 원<br>`FROM_150K_TO_300K` 15만~30만 원<br>`OVER_300K` 30만 원 이상<br>`UNKNOWN` 잘 모르겠어요 | `spendingTypes=[DO_NOT_REDUCE]`이면 `null` |
| 4 | `HOBBY_FREQUENCIES`<br>선택한 항목에 평소 얼마나 자주 결제하나요? | `hobby.frequencies: List<{spendingType, count}>` | 항목별 숫자 | `SUBSCRIPTION`: 0~20개<br>그 외: 4주당 0~31회 | key 집합과 개수가 `spendingTypes`와 정확히 일치<br>`DO_NOT_REDUCE`이면 `[]` |
| 5 | `HOBBY_SAVING_METHODS`<br>어떤 취미 절약 방식이라면 괜찮나요? | `hobby.savingMethods: List<String>` | 복수 선택 1~7개 | `WAIT_BEFORE_BUYING` 구매 전 하루 이상 기다리기<br>`SET_WEEKLY_LIMIT` 주간 구매 횟수 정하기<br>`USE_OWNED_FIRST` 가진 용품·콘텐츠 먼저 사용<br>`USE_CHEAPER_ALTERNATIVE` 무료·저렴한 대안 사용<br>`REVIEW_SUBSCRIPTIONS` 이용하지 않는 구독 점검<br>`KEEP_TIME_REDUCE_COST` 취미 시간은 유지하고 비용만 줄이기<br>`NO_HOBBY_MISSION` 취미 관련 미션은 원하지 않음 | `NO_HOBBY_MISSION` 단독 선택<br>`DO_NOT_REDUCE`이면 `[NO_HOBBY_MISSION]` |

`HOBBY_TYPES` 질문의 주관식 메타데이터는 다음과 같이 내려온다.

```json
{
  "textRules": [
    {
      "subjectOptionCode": "OTHER",
      "minimumLength": 1,
      "maximumLength": 50
    }
  ]
}
```

기타 취미 요청 예시:

```json
{
  "hobby": {
    "hobbies": ["READING", "OTHER"],
    "otherHobby": "보드게임",
    "spendingTypes": ["GOODS"],
    "monthlySpendingRange": "UNDER_50K",
    "frequencies": [
      {
        "spendingType": "GOODS",
        "count": 2
      }
    ],
    "savingMethods": ["WAIT_BEFORE_BUYING"]
  }
}
```

## 생활 `LIVING`

| 순서 | 질문 | 클라이언트 필드 | 방식 | 선택지·범위 | 조건 |
|---:|---|---|---|---|---|
| 1 | `LIVING_AREAS`<br>생활비 중 가장 먼저 바꾸고 싶은 영역은 무엇인가요? | `living.areas: List<String>` | 복수 선택 1~2개 | `SUBSCRIPTION` 정기구독<br>`ONLINE_SHOPPING` 온라인 쇼핑<br>`CLOTHING` 의류·잡화<br>`HOUSEHOLD_GOODS` 생활용품<br>`CONVENIENCE_PURCHASE` 편의점·소액 구매<br>`BEAUTY` 미용·뷰티<br>`EXERCISE_OR_LEARNING` 운동·자기계발<br>`UNKNOWN` 아직 잘 모르겠어요 | `UNKNOWN` 단독 선택 |
| 2 | `LIVING_MONTHLY_SPENDING`<br>선택한 영역에 월평균 얼마 정도 사용하나요? | `living.monthlySpendingRange: String?` | 단일 선택 | `UNDER_30K` 3만 원 미만<br>`FROM_30K_TO_100K` 3만~10만 원<br>`FROM_100K_TO_300K` 10만~30만 원<br>`OVER_300K` 30만 원 이상<br>`UNKNOWN` 잘 모르겠어요 | `areas=[UNKNOWN]`이면 `null` |
| 3 | `LIVING_FREQUENCIES`<br>해당 소비는 얼마나 자주 발생하나요? | `living.frequencies: List<{area, count}>` | 항목별 숫자 | `SUBSCRIPTION`: 0~20개<br>그 외: 4주당 0~31회 | key 집합과 개수가 `areas`와 정확히 일치<br>`UNKNOWN`이면 `[]` |
| 4 | `LIVING_TRIGGER`<br>예정에 없던 소비는 주로 언제 발생하나요? | `living.trigger: String` | 단일 선택 | `DISCOUNT_OR_LIMITED_SALE` 할인·한정판매<br>`AD_OR_SOCIAL_MEDIA` 광고·SNS·추천<br>`INVENTORY_UNCHECKED` 보유 재고를 확인하지 못했을 때<br>`STRESS_OR_BOREDOM` 스트레스·무료함<br>`CONVENIENCE` 편리함 때문에<br>`FORGOT_AUTO_PAYMENT` 자동결제를 잊어서<br>`RARELY_UNPLANNED` 예정에 없던 소비는 거의 없음 | `areas=[UNKNOWN]`이어도 필수 |
| 5 | `LIVING_SAVING_METHODS`<br>어떤 절약 행동을 실천할 수 있나요? | `living.savingMethods: List<String>` | 복수 선택 1~8개 | `WAIT_24_HOURS` 구매 전 24시간 기다리기<br>`USE_SHOPPING_LIST` 쇼핑 목록 사용<br>`CHECK_INVENTORY` 집에 있는 재고 확인<br>`LIMIT_FREQUENCY` 구매·이용 횟수 제한<br>`REVIEW_SUBSCRIPTIONS` 구독 이용 여부 점검<br>`CONSIDER_REUSE` 수리·대여·중고·재사용 검토<br>`EXCLUDE_NECESSARY_COST` 건강·교육 등 필요한 비용은 제외<br>`NO_LIVING_MISSION` 해당 영역의 미션은 원하지 않음 | `NO_LIVING_MISSION` 단독 선택 |

## 클라이언트 체크리스트

| 항목 | 처리 |
|---|---|
| 선택지 | 서버가 내려준 `option.code`를 그대로 제출 |
| 복수 선택 | 중복 제거 및 최소·최대 개수 적용 |
| 배타 선택 | `exclusiveOptionCodes` 선택 시 다른 값 해제 |
| 조건부 질문 | `dependsOnQuestionCode`, `skipWhenOptionCodes` 적용 |
| 숫자 | `numericRules`의 subject별 단위와 범위 적용 |
| 주관식 | `textRules`의 subject가 선택되면 추가 입력 UI 표시 |
| 기타 취미 | `OTHER` 선택 시 trim 후 1~50자 `otherHobby` 제출 |
| 최종 검증 | 프론트 검증과 별개로 서버가 모든 조합을 다시 검증 |
