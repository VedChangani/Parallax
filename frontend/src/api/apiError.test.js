import { describe, expect, it } from 'vitest';
import { ApiError } from './apiError.js';

describe('ApiError', () => {
  it('carries structured fields from an api-kind ProblemDetail', () => {
    const error = ApiError.fromProblemDetail(422, {
      title: 'Invalid strategy definition',
      detail: 'entryCondition.left: must be finite',
      field: 'entryCondition.left',
      status: 422,
    });

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe('api');
    expect(error.status).toBe(422);
    expect(error.title).toBe('Invalid strategy definition');
    expect(error.detail).toBe('entryCondition.left: must be finite');
    expect(error.field).toBe('entryCondition.left');
  });

  it('collects field-level validation errors', () => {
    const error = ApiError.fromProblemDetail(400, {
      title: 'Malformed request',
      detail: 'request failed validation',
      errors: [{ field: 'name', message: 'must not be blank' }],
    });

    expect(error.errors).toEqual([{ field: 'name', message: 'must not be blank' }]);
  });

  it('keeps unrecognized ProblemDetail extension properties under props', () => {
    const error = ApiError.fromProblemDetail(422, {
      title: 'Invalid backtest range',
      detail: 'range out of bounds',
      requestedStartDate: '2020-01-01',
      datasetFirstDate: '2020-06-01',
    });

    expect(error.props).toEqual({ requestedStartDate: '2020-01-01', datasetFirstDate: '2020-06-01' });
  });

  it('leaves props undefined when there are no extension properties', () => {
    const error = ApiError.fromProblemDetail(404, { title: 'Not found', detail: 'no such strategy' });
    expect(error.props).toBeUndefined();
  });

  it('marks a network failure with a distinct kind and no status', () => {
    const error = ApiError.network('fetch failed');
    expect(error.kind).toBe('network');
    expect(error.status).toBeUndefined();
  });

  it('marks an aborted request with a distinct kind', () => {
    const error = ApiError.aborted();
    expect(error.kind).toBe('aborted');
  });
});
