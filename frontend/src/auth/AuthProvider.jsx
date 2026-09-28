import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getMe, login as apiLogin, logout as apiLogout } from '../api/auth.js';
import * as immutableCache from '../api/immutableCache.js';
import { setUnauthorizedHandler } from '../api/httpClient.js';
import { AuthContext } from './AuthContext.js';

/** The channel name every tab of this app listens on (D-39). */
export const AUTH_CHANNEL_NAME = 'parallax-auth';

const IDENTITY_CHANGED = 'identity-changed';

/**
 * The root of the D-39 identity lifecycle: resolves the initial session
 * from `GET /api/auth/me`, exposes `login`/`logout`, and is the one place
 * that clears the immutable cache and bumps its epoch on every identity
 * change - login, logout, and an unexpected 401 from anywhere else in the
 * app (via `setUnauthorizedHandler`, since a plain data-fetch 401 has no
 * other way to reach this component).
 *
 * A `BroadcastChannel` keeps every open tab in sync: whichever tab changes
 * identity posts a message, and every tab (including the one that posted
 * it) reacts by clearing its cache and re-checking `/api/auth/me`. Some
 * environments (very old browsers, or a test's jsdom) lack
 * `BroadcastChannel` entirely; that tab still works correctly on its own,
 * it just cannot warn other tabs.
 */
export function AuthProvider({ children }) {
  const [state, setState] = useState({ status: 'loading', username: undefined });
  const channelRef = useRef(undefined);

  // D-39: matches useApiResource's own idiom - every setState below runs
  // inside a .then()/.catch() callback rather than directly in an async
  // function's body, which is what keeps a plain `refresh()` call in a
  // useEffect body from tripping react-hooks/set-state-in-effect (a state
  // update belongs in a callback reacting to an external event completing,
  // never synchronously in the effect itself).
  const refresh = useCallback(() => {
    return getMe().then(
      (me) => setState({ status: 'authenticated', username: me.username }),
      // getMe() passes skipUnauthorizedHandling, so any failure here -
      // including a genuine 401 - just means "not signed in".
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
      // D-37's CsrfLogoutHandler expires the XSRF-TOKEN cookie along with
      // the session, so without this the very next login attempt would
      // submit no CSRF token at all and fail with a 403 - re-bootstrapping
      // via GET /api/auth/me (same as on mount) re-establishes a fresh one.
      refresh();
    }
  }, [announceIdentityChanged, refresh]);

  const value = useMemo(() => ({ ...state, login, logout }), [state, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
