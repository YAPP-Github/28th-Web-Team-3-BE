import { arrivalRateScenario, commonThresholds } from './lib/config.js';
import { fullFlow } from './lib/flows.js';

export const options = {
  scenarios: {
    full_e2e: arrivalRateScenario('run'),
  },
  thresholds: commonThresholds(true),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function run() {
  fullFlow();
}
