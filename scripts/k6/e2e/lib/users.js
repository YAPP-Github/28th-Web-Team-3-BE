import { apiRequest, responseJson } from './http.js';

export function issueGuest(phase = 'measured') {
  const response = apiRequest({
    method: 'POST',
    path: '/api/auth/guest',
    body: { uuid: uuidV4() },
    expectedStatus: 201,
    name: 'POST /api/auth/guest',
    phase,
  });
  const payload = responseJson(response, 'guest authentication');
  if (!payload.accessToken) {
    throw new Error('guest authentication did not return accessToken');
  }
  return payload.accessToken;
}

function uuidV4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}
