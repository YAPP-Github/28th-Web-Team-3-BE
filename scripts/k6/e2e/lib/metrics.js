import { Rate, Trend } from 'k6/metrics';

export const flowDuration = new Trend('flow_duration', true);
export const flowFailures = new Rate('flow_failures');
export const missionGenerationDuration = new Trend(
  'mission_generation_duration',
  true,
);
export const missionCreationDuration = new Trend(
  'mission_creation_duration',
  true,
);
export const missionJobFailures = new Rate('mission_job_failures');
