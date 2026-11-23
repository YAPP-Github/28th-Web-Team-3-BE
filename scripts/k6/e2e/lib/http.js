import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';

export function apiRequest({
  method,
  path,
  token,
  body,
  expectedStatus,
  name,
  phase = 'measured',
}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = http.request(
    method,
    `${BASE_URL}${path}`,
    body === undefined ? null : JSON.stringify(body),
    {
      headers,
      tags: {
        name,
        endpoint: name,
        phase,
      },
    },
  );

  const passed = check(
    response,
    { [`${name} returns ${expectedStatus}`]: (result) => result.status === expectedStatus },
    { endpoint: name, phase },
  );
  if (!passed) {
    throw new Error(`${name} returned HTTP ${response.status}; expected ${expectedStatus}`);
  }
  return response;
}

export function responseJson(response, name) {
  try {
    return response.json();
  } catch (_) {
    throw new Error(`${name} returned an invalid JSON response`);
  }
}
