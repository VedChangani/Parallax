import { useCallback, useEffect, useRef, useState } from 'react';
import * as immutableCache from '../api/immutableCache.js';

/**
 * @template T
 * @typedef {object} UseApiResourceResult
 * @property {T | undefined} data
 * @property {import('../api/apiError.js').ApiError | undefined} error
 * @property {boolean} loading
 * @property {() => void} reload
 */

/**
 * Fetches one API resource on mount and whenever `deps` changes, with
 * proper request lifecycle handling: each call gets its own
 * `AbortController`, a superseded or unmounted request's result is
 * silently dropped (never shown as an error, never allowed to overwrite a
 * newer result), and `reload()` re-runs the same fetcher on demand.
 *
 * Every state update below is deferred by one microtask (`Promise.resolve().then`)
 * rather than made directly inside the effect body - functionally
 * equivalent (still runs before the fetch's own I/O settles), but keeps
 * every state transition reachable only from an async callback, matching
 * how React's effect model expects data-fetching effects to be shaped.
 *
 * @template T
 * @param {(signal: AbortSignal) => Promise<T>} fetcher
 * @param {ReadonlyArray<unknown>} deps
 * @param {{cacheKey?: string}} [options] - when `cacheKey` is set and
 *   already present in the immutable cache (see immutableCache.js), the
 *   cached value is used instead of calling `fetcher`; a successful fetch
 *   is stored under that key for later calls. Only pass this for a
 *   resource that qualifies as immutable.
 * @returns {UseApiResourceResult<T>}
 */
export function useApiResource(fetcher, deps, options = {}) {
  const { cacheKey } = options;
  const [state, setState] = useState({ data: undefined, error: undefined, loading: true });

  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const requestIdRef = useRef(0);

  const load = useCallback(() => {
    const requestId = ++requestIdRef.current;

    if (cacheKey && immutableCache.has(cacheKey)) {
      const cached = immutableCache.get(cacheKey);
      Promise.resolve().then(() => {
        if (requestIdRef.current !== requestId) return; // superseded by a later call
        setState({ data: cached, error: undefined, loading: false });
      });
      return () => {};
    }

    const controller = new AbortController();

    Promise.resolve().then(() => {
      if (requestIdRef.current !== requestId) return; // superseded by a later call
      setState((previous) => ({ ...previous, loading: true, error: undefined }));
    });

    fetcherRef.current(controller.signal).then(
      (data) => {
        if (requestIdRef.current !== requestId) return; // superseded by a later call
        if (cacheKey) immutableCache.set(cacheKey, data);
        setState({ data, error: undefined, loading: false });
      },
      (error) => {
        if (error?.kind === 'aborted') return; // never surfaced as a visible error
        if (requestIdRef.current !== requestId) return; // superseded by a later call
        setState({ data: undefined, error, loading: false });
      },
    );

    return () => controller.abort();
  }, [cacheKey]);

  useEffect(() => {
    return load();
    // `deps` is an intentionally caller-controlled dependency list.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const reload = useCallback(() => {
    load();
  }, [load]);

  return { ...state, reload };
}
