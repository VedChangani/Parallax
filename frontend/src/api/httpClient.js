import { ApiError } from './apiError.js';

/**
 * @typedef {object} RequestOptions
 * @property {string} [method] - defaults to "GET"
 * @property {Record<string, string>} [headers]
 * @property {unknown} [json] - a JSON-serializable request body
 * @property {FormData} [formData] - a multipart request body (mutually
 *   exclusive with `json`)
 * @property {AbortSignal} [signal]
 */

/**
 * The one fetch call site in the frontend. Every API call — present and
 * future — routes through this function, so method/header/body handling,
 * JSON parsing, and ProblemDetail-to-ApiError mapping live in exactly one
 * place. Always call it with a relative `/api/...` path; never a
 * hard-coded origin (the Vite dev proxy, and the production deployment,
 * own that).
 *
 * @param {string} path
 * @param {RequestOptions} [options]
 * @returns {Promise<unknown>} the parsed JSON body, or `undefined` for an
 *   empty successful response
 * @throws {ApiError}
 */
export async function request(path, options = {}) {
  const { method = 'GET', headers = {}, json, formData, signal } = options;

  if (json !== undefined && formData !== undefined) {
    throw new Error('request() cannot send both a json body and a formData body');
  }

  const finalHeaders = { ...headers };
  let body;

  if (formData !== undefined) {
    // Never set Content-Type manually for FormData - the browser must add
    // its own multipart boundary.
    body = formData;
  } else if (json !== undefined) {
    finalHeaders['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  }

  let response;
  try {
    response = await fetch(path, { method, headers: finalHeaders, body, signal });
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw ApiError.aborted();
    }
    throw ApiError.network(error?.message ?? 'network request failed');
  }

  const payload = await parseBody(response);

  if (!response.ok) {
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
