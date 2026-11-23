const DEFAULT_BASE_URL = 'http://localhost:8080';

export const BASE_URL = (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, '');
export const USER_COUNT = positiveInteger(__ENV.USER_COUNT, 20, 'USER_COUNT');
export const FLOW_RATE = positiveInteger(__ENV.FLOW_RATE, 2, 'FLOW_RATE');
export const FLOW_DURATION = __ENV.FLOW_DURATION || '10s';
export const MISSION_TIMEOUT_MS = positiveInteger(
  __ENV.MISSION_TIMEOUT_MS,
  30_000,
  'MISSION_TIMEOUT_MS',
);
export const MISSION_GENERATION_SLO_MS = 30_000;
export const THINK_TIME_ENABLED = (__ENV.THINK_TIME_ENABLED || 'true') === 'true';

assertSafeTarget();

export function arrivalRateScenario(exec) {
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate: FLOW_RATE,
    timeUnit: '1s',
    duration: FLOW_DURATION,
    preAllocatedVUs: USER_COUNT,
    maxVUs: USER_COUNT,
    gracefulStop: '2m',
  };
}

export function simultaneousUsersScenario(exec) {
  return {
    executor: 'per-vu-iterations',
    exec,
    vus: USER_COUNT,
    iterations: 1,
    maxDuration: '2m',
    gracefulStop: '30s',
  };
}

export function commonThresholds(includeMissionGeneration) {
  const thresholds = {
    'http_req_failed{phase:measured}': ['rate<0.01'],
    'http_req_duration{phase:measured}': ['p(95)<500'],
    'checks{phase:measured}': ['rate>0.99'],
    flow_failures: ['rate==0'],
    dropped_iterations: ['count==0'],
  };

  if (includeMissionGeneration) {
    thresholds.mission_job_failures = ['rate==0'];
    thresholds.mission_generation_duration = [
      `p(95)<${MISSION_GENERATION_SLO_MS}`,
    ];
  }

  return thresholds;
}

function assertSafeTarget() {
  if (!/^https?:\/\//.test(BASE_URL)) {
    throw new Error('BASE_URL must start with http:// or https://');
  }

  const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/i.test(BASE_URL);
  if (!isLocal && __ENV.ALLOW_NON_LOCAL_LOAD !== 'true') {
    throw new Error(
      'Refusing a non-local load test. Set ALLOW_NON_LOCAL_LOAD=true after confirming the target.',
    );
  }
}

function positiveInteger(rawValue, defaultValue, name) {
  if (rawValue === undefined || rawValue === '') {
    return defaultValue;
  }

  const parsed = Number(rawValue);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}
