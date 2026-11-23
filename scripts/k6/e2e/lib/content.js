import { apiRequest, responseJson } from './http.js';

export function browseAndBookmarkContent(token) {
  const policies = fetchList(token, '/api/policies?page=0&size=20', 'GET /api/policies');
  if (policies.length > 0) {
    viewAndBookmark(token, 'policies', policies[0].id);
  }

  const tips = fetchList(token, '/api/tips?page=0&size=20', 'GET /api/tips');
  if (tips.length > 0) {
    viewAndBookmark(token, 'tips', tips[0].id);
  }

  apiRequest({
    method: 'GET',
    path: '/api/bookmarks',
    token,
    expectedStatus: 200,
    name: 'GET /api/bookmarks',
  });
}

function fetchList(token, path, name) {
  const response = apiRequest({
    method: 'GET',
    path,
    token,
    expectedStatus: 200,
    name,
  });
  const payload = responseJson(response, name);
  if (!Array.isArray(payload)) {
    throw new Error(`${name} did not return an array`);
  }
  return payload;
}

function viewAndBookmark(token, resource, id) {
  apiRequest({
    method: 'GET',
    path: `/api/${resource}/${id}`,
    token,
    expectedStatus: 200,
    name: `GET /api/${resource}/:id`,
  });
  apiRequest({
    method: 'POST',
    path: `/api/${resource}/${id}/bookmark`,
    token,
    expectedStatus: 204,
    name: `POST /api/${resource}/:id/bookmark`,
  });
}
