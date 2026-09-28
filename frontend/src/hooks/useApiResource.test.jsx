import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/apiError.js';
import * as immutableCache from '../api/immutableCache.js';
import { useApiResource } from './useApiResource.js';

describe('useApiResource', () => {
  it('starts in a loading state with no data', () => {
    const fetcher = vi.fn(() => new Promise(() => {}));
    const { result } = renderHook(() => useApiResource(fetcher, []));

    expect(result.current.loading).toBe(true);
    expect(result.current.data).toBeUndefined();
    expect(result.current.error).toBeUndefined();
  });

  it('resolves with data on success', async () => {
    const fetcher = vi.fn().mockResolvedValue({ id: 1 });
    const { result } = renderHook(() => useApiResource(fetcher, []));

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.data).toEqual({ id: 1 });
    expect(result.current.error).toBeUndefined();
  });

  it('surfaces a non-aborted failure as an error', async () => {
    const error = new ApiError({ kind: 'api', status: 404 });
    const fetcher = vi.fn().mockRejectedValue(error);
    const { result } = renderHook(() => useApiResource(fetcher, []));

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.error).toBe(error);
    expect(result.current.data).toBeUndefined();
  });

  it('never surfaces an aborted request as a visible error', async () => {
    const fetcher = vi.fn().mockRejectedValue(ApiError.aborted());
    const { result } = renderHook(() => useApiResource(fetcher, []));

    await waitFor(() => expect(fetcher).toHaveBeenCalled());
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(result.current.error).toBeUndefined();
  });

  it('re-runs the fetcher on reload()', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce({ id: 1 }).mockResolvedValueOnce({ id: 2 });
    const { result } = renderHook(() => useApiResource(fetcher, []));

    await waitFor(() => expect(result.current.data).toEqual({ id: 1 }));

    act(() => {
      result.current.reload();
    });

    await waitFor(() => expect(result.current.data).toEqual({ id: 2 }));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('never lets a superseded request overwrite newer state', async () => {
    let resolveFirst;
    const first = new Promise((resolve) => {
      resolveFirst = resolve;
    });
    const fetcher = vi.fn().mockReturnValueOnce(first).mockResolvedValueOnce({ id: 'second' });

    const { result, rerender } = renderHook(({ id }) => useApiResource(fetcher, [id]), {
      initialProps: { id: 1 },
    });

    rerender({ id: 2 });
    await waitFor(() => expect(result.current.data).toEqual({ id: 'second' }));

    await act(async () => {
      resolveFirst({ id: 'first' });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(result.current.data).toEqual({ id: 'second' });
  });

  it('drops a cache write from a response that resolves after the identity epoch has moved on (D-39)', async () => {
    let resolveFetch;
    const fetcher = vi.fn(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve;
        }),
    );
    renderHook(() => useApiResource(fetcher, [], { cacheKey: 'stale-epoch-key' }));

    await waitFor(() => expect(fetcher).toHaveBeenCalled());

    // Simulates a login/logout/401 elsewhere in the app while this request is still in flight.
    immutableCache.bumpEpoch();

    await act(async () => {
      resolveFetch({ id: 1 });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(immutableCache.has('stale-epoch-key')).toBe(false);
  });
});
