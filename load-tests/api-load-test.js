import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },  // Aquecimento
    { duration: '3m', target: 200 }, // Carga pesada
    { duration: '3m', target: 500 }, // Stress - Aqui o filho chora e a mãe não vê huahuahua
    { duration: '2m', target: 0 },   // Resfriamento
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'], // Aceitamos até 5% de erro no estresse
    http_req_duration: ['p(95)<2000'], // Se passar de 2s, a API tá morrendo
  },
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const email = __ENV.LOAD_TEST_EMAIL || 'loadtest@example.com';
const password = __ENV.LOAD_TEST_PASSWORD || 'LoadTest#123';

function registerUser() {
  const payload = JSON.stringify({
    name: 'Load Test User',
    email: email,
    password: password,
    confirmPassword: password
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
    throw new Error(`Login failed with status ${loginResponse.status}: ${loginResponse.body}`);
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
