import { ApiError } from './apiError.js';

const SAFE_METHODS = new Set(['GET', 'HEAD']);

let unauthorizedHandler = () => {};

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler ?? (() => {});
}

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
