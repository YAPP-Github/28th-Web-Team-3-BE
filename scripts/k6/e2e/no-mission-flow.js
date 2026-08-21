import { arrivalRateScenario, commonThresholds } from './lib/config.js';
import { noMissionFlow } from './lib/flows.js';

export const options = {
  scenarios: {
    no_mission_e2e: arrivalRateScenario('run'),
  },
  thresholds: commonThresholds(false),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function run() {
  noMissionFlow();
}
