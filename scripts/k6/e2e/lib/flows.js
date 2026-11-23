import exec from 'k6/execution';
import { fail } from 'k6';
import { browseAndBookmarkContent } from './content.js';
import { flowDuration, flowFailures } from './metrics.js';
import { generateAndConfirmMission, runMissionLifecycle } from './missions.js';
import { completeOnboarding, viewGoal } from './onboarding.js';
import { issueGuest } from './users.js';

export function fullFlow() {
  runMeasuredFlow('full_e2e', () => {
    const token = issueGuest();
    completeOnboarding(token);
    viewGoal(token);
    browseAndBookmarkContent(token);
    runMissionLifecycle(token, exec.scenario.iterationInTest);
  });
}

export function noMissionFlow() {
  runMeasuredFlow('no_mission_e2e', () => {
    const token = issueGuest();
    completeOnboarding(token);
    viewGoal(token);
    browseAndBookmarkContent(token);
  });
}

export function prepareMissionUsers(count) {
  const users = [];
  for (let index = 0; index < count; index += 1) {
    const token = issueGuest('setup');
    completeOnboarding(token, 'setup', false);
    users.push({ token });
  }
  return users;
}

export function missionOnlyFlow(users) {
  runMeasuredFlow('mission_only', () => {
    const iteration = exec.scenario.iterationInTest;
    const user = users[iteration];
    if (!user || !user.token) {
      throw new Error(`no prepared mission user for iteration ${iteration}`);
    }
    generateAndConfirmMission(user.token, iteration);
  });
}

function runMeasuredFlow(flow, action) {
  const startedAt = Date.now();
  try {
    action();
    flowFailures.add(false, { flow });
  } catch (error) {
    flowFailures.add(true, { flow });
    console.error(`[${flow}] ${error.message}`);
    fail(error.message);
  } finally {
    flowDuration.add(Date.now() - startedAt, { flow });
  }
}
