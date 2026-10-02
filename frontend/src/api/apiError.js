const KNOWN_PROBLEM_PROPERTIES = new Set(['type', 'title', 'status', 'detail', 'instance', 'field', 'errors']);

export class ApiError extends Error {
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

  static network(message) {
    return new ApiError({ kind: 'network', detail: message });
  }

  static aborted() {
    return new ApiError({ kind: 'aborted', detail: 'request was aborted' });
  }

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
