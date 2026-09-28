import { request } from './httpClient.js';

/**
 * Authentication endpoint functions (D-37/D-38/D-39). Every function here
 * is a thin wrapper around the single fetch call site in httpClient.js —
 * no component ever calls request()/fetch() directly for one of these.
 */

/**
 * `GET /api/auth/me` - the identity check `AuthProvider` calls on load,
 * and the request that bootstraps the `XSRF-TOKEN` cookie (D-37). A 401
 * here means "anonymous", not a session that expired, so it must never
 * trigger the global unauthorized flow.
 *
 * @returns {Promise<{username: string}>}
 */
export function getMe() {
  return request('/api/auth/me', { skipUnauthorizedHandling: true });
}

/**
 * `POST /api/auth/login` - form-urlencoded credentials, per D-37's
 * `formLogin`. A 401 here means "invalid username or password", an
 * expected outcome of a login attempt, so it must never trigger the
 * global unauthorized flow either.
 *
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{username: string}>}
 */
export function login(username, password) {
  return request('/api/auth/login', {
    method: 'POST',
    form: { username, password },
    skipUnauthorizedHandling: true,
  });
}

/** `POST /api/auth/logout` (D-37) - invalidates the session. */
export function logout() {
  return request('/api/auth/logout', { method: 'POST' });
}

/**
 * `POST /api/auth/register` (D-38). Never logs the caller in - a
 * successful call still requires a separate {@link login}.
 *
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{username: string}>}
 */
export function register(username, password) {
  return request('/api/auth/register', { method: 'POST', json: { username, password } });
}
