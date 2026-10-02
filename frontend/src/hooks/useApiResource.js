import { useCallback, useEffect, useRef, useState } from 'react';
import * as immutableCache from '../api/immutableCache.js';

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
        if (requestIdRef.current !== requestId) return;
        setState({ data: cached, error: undefined, loading: false });
      });
      return () => {};
    }

    const controller = new AbortController();
    const requestEpoch = immutableCache.currentEpoch();

    Promise.resolve().then(() => {
      if (requestIdRef.current !== requestId) return;
      setState((previous) => ({ ...previous, loading: true, error: undefined }));
    });

    fetcherRef.current(controller.signal).then(
      (data) => {
        if (requestIdRef.current !== requestId) return;
        if (cacheKey) immutableCache.set(cacheKey, data, requestEpoch);
        setState({ data, error: undefined, loading: false });
      },
      (error) => {
        if (error?.kind === 'aborted') return;
        if (requestIdRef.current !== requestId) return;
        setState({ data: undefined, error, loading: false });
      },
    );

    return () => controller.abort();
  }, [cacheKey]);

  useEffect(() => {
    return load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const reload = useCallback(() => {
    load();
  }, [load]);

  return { ...state, reload };
}
