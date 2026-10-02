import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getMe, login as apiLogin, logout as apiLogout } from '../api/auth.js';
import * as immutableCache from '../api/immutableCache.js';
import { setUnauthorizedHandler } from '../api/httpClient.js';
import { AuthContext } from './AuthContext.js';

export const AUTH_CHANNEL_NAME = 'parallax-auth';

const IDENTITY_CHANGED = 'identity-changed';

export function AuthProvider({ children }) {
  const [state, setState] = useState({ status: 'loading', username: undefined });
  const channelRef = useRef(undefined);

  const refresh = useCallback(() => {
    return getMe().then(
      (me) => setState({ status: 'authenticated', username: me.username }),
      () => setState({ status: 'anonymous', username: undefined }),
    );
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') {
      return undefined;
    }
    const channel = new BroadcastChannel(AUTH_CHANNEL_NAME);
    channelRef.current = channel;
    channel.onmessage = (event) => {
      if (event.data?.type === IDENTITY_CHANGED) {
        immutableCache.bumpEpoch();
        refresh();
      }
    };
    return () => {
      channel.close();
      channelRef.current = undefined;
    };
  }, [refresh]);

  const announceIdentityChanged = useCallback(() => {
    channelRef.current?.postMessage({ type: IDENTITY_CHANGED });
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      immutableCache.bumpEpoch();
      setState({ status: 'anonymous', username: undefined });
      announceIdentityChanged();
    });
    return () => setUnauthorizedHandler(undefined);
  }, [announceIdentityChanged]);

  const login = useCallback(
    async (username, password) => {
      const result = await apiLogin(username, password);
      immutableCache.bumpEpoch();
      setState({ status: 'authenticated', username: result.username });
      announceIdentityChanged();
    },
    [announceIdentityChanged],
  );

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      immutableCache.bumpEpoch();
      setState({ status: 'anonymous', username: undefined });
      announceIdentityChanged();
      refresh();
    }
  }, [announceIdentityChanged, refresh]);

  const value = useMemo(() => ({ ...state, login, logout }), [state, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
