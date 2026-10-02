import { request } from './httpClient.js';

export function getMe() {
  return request('/api/auth/me', { skipUnauthorizedHandling: true });
}

export function login(username, password) {
  return request('/api/auth/login', {
    method: 'POST',
    form: { username, password },
    skipUnauthorizedHandling: true,
  });
}

export function logout() {
  return request('/api/auth/logout', { method: 'POST' });
}

export function register(username, password) {
  return request('/api/auth/register', { method: 'POST', json: { username, password } });
}
