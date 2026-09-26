/**
 * @typedef {'api' | 'network' | 'aborted'} ApiErrorKind
 */

/**
 * A single field-level validation error, as returned inside a
 * ProblemDetail's `errors` array (see {@link import('./types.js').ProblemDetail}).
 * @typedef {object} ApiFieldError
 * @property {string} field
 * @property {string} message
 */

const KNOWN_PROBLEM_PROPERTIES = new Set(['type', 'title', 'status', 'detail', 'instance', 'field', 'errors']);

/**
 * The one error type every API-client consumer sees. Wraps a failed HTTP
 * request (kind `"api"`, a parsed backend ProblemDetail), a transport
 * failure (kind `"network"`), or a deliberately cancelled request (kind
 * `"aborted"`) behind one shape.
 */
export class ApiError extends Error {
  /**
   * @param {object} params
   * @param {ApiErrorKind} params.kind
   * @param {number} [params.status]
   * @param {string} [params.title]
   * @param {string} [params.detail]
   * @param {string} [params.field]
   * @param {ApiFieldError[]} [params.errors]
   * @param {Record<string, unknown>} [params.props] - any other ProblemDetail
   *   extension property the backend attached (e.g. `line`, `date`,
   *   `requestedStartDate`).
   */
  constructor({ kind, status, title, detail, field, errors, props } = {}) {
    super(detail || title || 'API request failed');
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.field = field;
    this.errors = errors;
    this.props = props;
  }

  /** @param {string} message */
  static network(message) {
    return new ApiError({ kind: 'network', detail: message });
  }

  static aborted() {
    return new ApiError({ kind: 'aborted', detail: 'request was aborted' });
  }

  /**
   * @param {number} status
   * @param {import('./types.js').ProblemDetail | undefined} problem
   */
  static fromProblemDetail(status, problem) {
    const body = problem ?? {};
    const props = {};
    for (const key of Object.keys(body)) {
      if (!KNOWN_PROBLEM_PROPERTIES.has(key)) {
        props[key] = body[key];
      }
    }
    return new ApiError({
      kind: 'api',
      status,
      title: body.title,
      detail: body.detail,
      field: body.field,
      errors: body.errors,
      props: Object.keys(props).length > 0 ? props : undefined,
    });
  }
}
