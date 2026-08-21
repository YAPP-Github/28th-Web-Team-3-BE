import { sleep } from 'k6';
import { THINK_TIME_ENABLED } from './config.js';
import { apiRequest, responseJson } from './http.js';

export function completeOnboarding(token, phase = 'measured', useThinkTime = true) {
  patchProfile(token, { birthDate: '1998-03-01', address: 'SEOUL' }, phase);
  think(useThinkTime);

  apiRequest({
    method: 'GET',
    path: '/api/onboarding/profile',
    token,
    expectedStatus: 200,
    name: 'GET /api/onboarding/profile',
    phase,
  });

  patchProfile(
    token,
    { monthlySalaryManwon: 350, monthlySavingManwon: 100 },
    phase,
  );
  think(useThinkTime);
  patchProfile(token, { netWorthManwon: 1800 }, phase);
  think(useThinkTime);
  patchProfile(token, { goalPeriodMonths: 24 }, phase);

  apiRequest({
    method: 'GET',
    path: '/api/onboarding/report',
    token,
    expectedStatus: 200,
    name: 'GET /api/onboarding/report',
    phase,
  });
  think(useThinkTime);

  const previewResponse = apiRequest({
    method: 'GET',
    path: '/api/v2/onboarding/goal-preview',
    token,
    expectedStatus: 200,
    name: 'GET /api/v2/onboarding/goal-preview',
    phase,
  });
  const preview = responseJson(previewResponse, 'goal preview');
  if (!Number.isInteger(preview.recommendedMonthlySavingManwon)) {
    throw new Error('goal preview did not return recommendedMonthlySavingManwon');
  }

  apiRequest({
    method: 'POST',
    path: '/api/v2/onboarding/goal',
    token,
    body: { monthlySavingManwon: preview.recommendedMonthlySavingManwon },
    expectedStatus: 201,
    name: 'POST /api/v2/onboarding/goal',
    phase,
  });
  return preview;
}

export function viewGoal(token, phase = 'measured') {
  apiRequest({
    method: 'GET',
    path: '/api/v2/goal',
    token,
    expectedStatus: 200,
    name: 'GET /api/v2/goal',
    phase,
  });
}

function patchProfile(token, body, phase) {
  apiRequest({
    method: 'PATCH',
    path: '/api/onboarding/profile',
    token,
    body,
    expectedStatus: 200,
    name: 'PATCH /api/onboarding/profile',
    phase,
  });
}

function think(enabled) {
  if (enabled && THINK_TIME_ENABLED) {
    sleep(0.3 + Math.random() * 0.7);
  }
}
