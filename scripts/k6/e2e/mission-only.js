import {
  commonThresholds,
  simultaneousUsersScenario,
  USER_COUNT,
} from './lib/config.js';
import { missionOnlyFlow, prepareMissionUsers } from './lib/flows.js';

export const options = {
  scenarios: {
    mission_only: simultaneousUsersScenario('run'),
  },
  thresholds: commonThresholds(true),
  setupTimeout: '5m',
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  return prepareMissionUsers(USER_COUNT);
}

export function run(users) {
  missionOnlyFlow(users);
}
