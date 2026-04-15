import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '30s', target: 15 },
    { duration: '45s', target: 30 },
    { duration: '45s', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1000'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const email = __ENV.LOAD_TEST_EMAIL || 'loadtest@example.com';
const password = __ENV.LOAD_TEST_PASSWORD || 'LoadTest#123';

function registerUser() {
  const payload = JSON.stringify({
    name: 'Load Test User',
    email,
    password,
  });

  const response = http.post(`${baseUrl}/api/auth/register`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'register' },
  });

  check(response, {
    'register status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
}

function loginUser() {
  const payload = JSON.stringify({
    email,
    password,
  });

  const response = http.post(`${baseUrl}/api/auth/login`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'login' },
  });

  check(response, {
    'login status is 200': (r) => r.status === 200,
    'login returned access token': (r) => {
      try {
        const body = r.json();
        return Boolean(body.accessToken);
      } catch (e) {
        return false;
      }
    },
  });

  return response;
}

export function setup() {
  registerUser();
  const loginResponse = loginUser();

  if (loginResponse.status !== 200) {
    throw new Error(`Login failed in setup: status=${loginResponse.status}, body=${loginResponse.body}`);
  }

  const body = loginResponse.json();

  return {
    accessToken: body.accessToken,
  };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.accessToken}`,
  };

  const meResponse = http.get(`${baseUrl}/api/auth/me`, {
    headers,
    tags: { endpoint: 'me' },
  });

  check(meResponse, {
    'me status is 200': (r) => r.status === 200,
  });

  const applicationsResponse = http.get(`${baseUrl}/api/applications?page=0&size=10`, {
    headers,
    tags: { endpoint: 'applications' },
  });

  check(applicationsResponse, {
    'applications status is 200': (r) => r.status === 200,
  });

  sleep(1);
}
