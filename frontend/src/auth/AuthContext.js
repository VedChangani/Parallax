import { createContext, useContext } from 'react';

/**
 * @typedef {'loading' | 'anonymous' | 'authenticated'} AuthStatus
 */

/**
 * @typedef {object} AuthContextValue
 * @property {AuthStatus} status
 * @property {string | undefined} username - set only when `status === 'authenticated'`
 * @property {(username: string, password: string) => Promise<void>} login
 * @property {() => Promise<void>} logout
 */

/** @type {import('react').Context<AuthContextValue | undefined>} */
export const AuthContext = createContext(undefined);

/** @returns {AuthContextValue} */
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return value;
}
