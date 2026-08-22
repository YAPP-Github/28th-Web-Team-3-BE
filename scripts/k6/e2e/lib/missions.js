import { sleep } from 'k6';
import { MISSION_TIMEOUT_MS } from './config.js';
import { apiRequest, responseJson } from './http.js';
import {
  missionCreationDuration,
  missionGenerationDuration,
  missionJobFailures,
} from './metrics.js';

const MISSION_INPUTS = [
  {
    category: 'MEAL',
    item: 'DELIVERY_FOOD',
    baselineFrequency: 5,
    baselineAmountWon: 50_000,
  },
  {
    category: 'LIVING',
    item: 'HOUSEHOLD_GOODS',
    baselineFrequency: 3,
    baselineAmountWon: 30_000,
  },
  {
    category: 'HOBBY',
    item: 'CLASS',
    baselineFrequency: 2,
    baselineAmountWon: 80_000,
  },
];

export function generateAndConfirmMission(token, inputIndex = 0) {
  const startedAt = Date.now();
  const input = MISSION_INPUTS[inputIndex % MISSION_INPUTS.length];
  const createResponse = apiRequest({
    method: 'POST',
    path: '/api/missions/generation-jobs',
    token,
    body: input,
    expectedStatus: 202,
    name: 'POST /api/missions/generation-jobs',
  });
  let job = responseJson(createResponse, 'mission generation request');
  if (!job.jobId) {
    throw new Error('mission generation request did not return jobId');
  }

  job = waitForGeneration(token, job, startedAt);
  const generationElapsed = Date.now() - startedAt;
  missionGenerationDuration.add(generationElapsed);
  missionJobFailures.add(false);

  const draftsResponse = apiRequest({
    method: 'GET',
    path: `/api/missions/generation-jobs/${job.jobId}/drafts`,
    token,
    expectedStatus: 200,
    name: 'GET /api/missions/generation-jobs/:jobId/drafts',
  });
  const drafts = responseJson(draftsResponse, 'mission drafts');
  const draftIds = flattenDraftIds(drafts);
  if (draftIds.length === 0) {
    throw new Error(`mission generation job ${job.jobId} returned no drafts`);
  }

  const confirmResponse = apiRequest({
    method: 'POST',
    path: `/api/missions/generation-jobs/${job.jobId}/confirm`,
    token,
    body: { selectedDraftIds: draftIds.slice(0, 2) },
    expectedStatus: 200,
    name: 'POST /api/missions/generation-jobs/:jobId/confirm',
  });
  const confirmation = responseJson(confirmResponse, 'mission confirmation');
  if (!Array.isArray(confirmation.missions) || confirmation.missions.length === 0) {
    throw new Error(`mission generation job ${job.jobId} created no missions`);
  }

  missionCreationDuration.add(Date.now() - startedAt);
  return confirmation.missions;
}

export function runMissionLifecycle(token, inputIndex = 0) {
  apiRequest({
    method: 'GET',
    path: '/api/missions/catalog',
    token,
    expectedStatus: 200,
    name: 'GET /api/missions/catalog',
  });

  const createdMissions = generateAndConfirmMission(token, inputIndex);

  apiRequest({
    method: 'GET',
    path: '/api/missions?status=ACTIVE',
    token,
    expectedStatus: 200,
    name: 'GET /api/missions',
  });
  apiRequest({
    method: 'GET',
    path: '/api/missions/progress',
    token,
    expectedStatus: 200,
    name: 'GET /api/missions/progress',
  });
  apiRequest({
    method: 'PATCH',
    path: `/api/missions/RECOMMENDED/${createdMissions[0].id}/complete`,
    token,
    expectedStatus: 200,
    name: 'PATCH /api/missions/:source/:missionId/complete',
  });
  apiRequest({
    method: 'GET',
    path: '/api/missions/progress',
    token,
    expectedStatus: 200,
    name: 'GET /api/missions/progress',
  });
}

function waitForGeneration(token, initialJob, startedAt) {
  let job = initialJob;
  while (job.status !== 'SUCCEEDED') {
    if (job.status === 'FAILED') {
      recordMissionFailure(startedAt);
      throw new Error(
        `mission generation job ${job.jobId} failed with ${job.failureCode || 'unknown failure'}`,
      );
    }

    const elapsed = Date.now() - startedAt;
    if (elapsed >= MISSION_TIMEOUT_MS) {
      recordMissionFailure(startedAt);
      throw new Error(`mission generation job ${job.jobId} exceeded ${MISSION_TIMEOUT_MS}ms`);
    }

    const pollingIntervalMillis = Number(job.pollingIntervalMillis) || 2_000;
    sleep(Math.min(pollingIntervalMillis, MISSION_TIMEOUT_MS - elapsed) / 1_000);
    const statusResponse = apiRequest({
      method: 'GET',
      path: `/api/missions/generation-jobs/${job.jobId}`,
      token,
      expectedStatus: 200,
      name: 'GET /api/missions/generation-jobs/:jobId',
    });
    job = responseJson(statusResponse, 'mission generation status');
  }
  return job;
}

function recordMissionFailure(startedAt) {
  missionGenerationDuration.add(Date.now() - startedAt);
  missionJobFailures.add(true);
}

function flattenDraftIds(payload) {
  if (!payload || !Array.isArray(payload.categories)) {
    return [];
  }
  return payload.categories.reduce((ids, category) => {
    if (!category || !Array.isArray(category.drafts)) {
      return ids;
    }
    category.drafts.forEach((draft) => {
      if (draft.id) {
        ids.push(draft.id);
      }
    });
    return ids;
  }, []);
}
