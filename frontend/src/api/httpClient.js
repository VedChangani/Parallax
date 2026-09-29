import { ApiError } from './apiError.js';

/**
 * @typedef {object} RequestOptions
 * @property {string} [method] - defaults to "GET"
 * @property {Record<string, string>} [headers]
 * @property {unknown} [json] - a JSON-serializable request body
 * @property {FormData} [formData] - a multipart request body (mutually
 *   exclusive with `json`/`form`)
 * @property {URLSearchParams | Record<string, string>} [form] - an
 *   `application/x-www-form-urlencoded` body (mutually exclusive with
 *   `json`/`formData`) - only `POST /api/auth/login` uses this today.
 * @property {AbortSignal} [signal]
 * @property {'json' | 'text'} [responseType] - how a *successful* body is
 *   read: `'json'` (default) parses it as JSON; `'text'` returns it as a
 *   string exactly as received (used for CSV exports). An error response
 *   is always a JSON ProblemDetail either way.
 * @property {boolean} [skipUnauthorizedHandling] - D-39: a 401 from this
 *   call never invokes the registered unauthorized handler. Used only by
 *   `api/auth.js`'s `getMe`/`login`: a bootstrap identity check and a
 *   login attempt are the two calls where a 401 is an expected, normal
 *   outcome (anonymous / invalid credentials) rather than a session that
 *   just expired.
 */

const SAFE_METHODS = new Set(['GET', 'HEAD']);

let unauthorizedHandler = () => {};

/**
 * D-39: registers the one callback invoked whenever any {@link request}
 * call (other than one passing `skipUnauthorizedHandling`) receives a 401
 * - normally `AuthProvider`, which clears the identity-scoped state
 * (immutable cache, auth state) and redirects to the login page. A module-
 * level singleton, not React context, because this is the one non-React
 * fetch call site and every request - including ones fired outside a
 * component's render, e.g. from `useApiResource` - must reach it the same
 * way.
 *
 * @param {() => void} handler
 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler ?? (() => {});
}

/**
 * Reads a cookie's raw value from `document.cookie`. Used only for
 * `XSRF-TOKEN` (D-37's double-submit CSRF cookie, deliberately not
 * `HttpOnly` so this can read it).
 *
 * @param {string} name
 * @returns {string | undefined}
 */
function readCookie(name) {
  const prefix = `${name}=`;
  const cookies = document.cookie ? document.cookie.split('; ') : [];
  for (const cookie of cookies) {
    if (cookie.startsWith(prefix)) {
      return decodeURIComponent(cookie.slice(prefix.length));
    }
  }
  return undefined;
}

/**
 * The one fetch call site in the frontend. Every API call — present and
 * future — routes through this function, so method/header/body handling,
 * JSON parsing, and ProblemDetail-to-ApiError mapping live in exactly one
 * place. Always call it with a relative `/api/...` path; never a
 * hard-coded origin (the Vite dev proxy, and the production deployment,
 * own that).
 *
 * D-39: every request carries the session cookie explicitly
 * (`credentials: 'same-origin'`), and every state-changing request (any
 * method but GET/HEAD) carries `X-XSRF-TOKEN`, read from the `XSRF-TOKEN`
 * cookie the backend's D-37 CSRF filter sets.
 *
 * @param {string} path
 * @param {RequestOptions} [options]
 * @returns {Promise<unknown>} the parsed JSON body, or `undefined` for an
 *   empty successful response
 * @throws {ApiError}
 */
export async function request(path, options = {}) {
  const {
    method = 'GET',
    headers = {},
    json,
    formData,
    form,
    signal,
    responseType = 'json',
    skipUnauthorizedHandling = false,
  } = options;

  const bodyOptionCount = [json, formData, form].filter((value) => value !== undefined).length;
  if (bodyOptionCount > 1) {
    throw new Error('request() cannot send more than one of a json, formData, or form body');
  }

  const finalHeaders = { ...headers };
  let body;

  if (formData !== undefined) {
    // Never set Content-Type manually for FormData - the browser must add
    // its own multipart boundary.
    body = formData;
  } else if (form !== undefined) {
    finalHeaders['Content-Type'] = 'application/x-www-form-urlencoded';
    body = form instanceof URLSearchParams ? form.toString() : new URLSearchParams(form).toString();
  } else if (json !== undefined) {
    finalHeaders['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  }

  if (!SAFE_METHODS.has(method.toUpperCase())) {
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) {
      finalHeaders['X-XSRF-TOKEN'] = csrfToken;
    }
  }

  let response;
  try {
    response = await fetch(path, { method, headers: finalHeaders, body, signal, credentials: 'same-origin' });
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw ApiError.aborted();
    }
    throw ApiError.network(error?.message ?? 'network request failed');
  }

  if (response.ok && responseType === 'text') {
    return response.text();
  }

  const payload = await parseBody(response);

  if (!response.ok) {
    if (response.status === 401 && !skipUnauthorizedHandling) {
      unauthorizedHandler();
    }
    throw ApiError.fromProblemDetail(response.status, payload);
  }

  return payload;
}

/**
 * Parses a response body as JSON when present, and safely returns
 * `undefined` for an empty successful response (e.g. 201/204 with no
 * body). A malformed JSON body is reported as a network-class failure
 * rather than left to throw a raw `SyntaxError` at the call site.
 *
 * @param {Response} response
 * @returns {Promise<unknown>}
 */
async function parseBody(response) {
  if (response.status === 204 || response.status === 205) {
    return undefined;
  }

  const contentType = response.headers.get('content-type') ?? '';
  const isJson = contentType.includes('json');
  if (!isJson) {
    return undefined;
  }

  const text = await response.text();
  if (text.length === 0) {
    return undefined;
  }

  try {
    return JSON.parse(text);
  } catch {
    throw ApiError.network('received a malformed JSON response');
  }
}
