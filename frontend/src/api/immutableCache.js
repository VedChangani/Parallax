/**
 * A Map-based, session-lifetime, GET-only cache keyed by request URL.
 * No TTL and no persistence beyond this page session (a reload clears it).
 *
 * Use ONLY for genuinely immutable resources - the response body for a
 * given key can never change once it has been fetched once:
 *   - strategy versions   (GET /api/strategies/:id/versions/:version)
 *   - dataset versions    (GET /api/datasets/:id/versions/:version,
 *                          GET /api/datasets/:id/versions/:version/bars)
 *   - a completed backtest run and its child resources
 *     (GET /api/backtest-runs/:id, .../equity-curve, .../trades,
 *      .../rejections)
 *
 * Never cache a mutable resource: strategy/dataset list or detail (their
 * `latestVersionNumber` changes as new versions are created) or the
 * backtest run list (grows as new runs are created).
 *
 * D-39: an "immutable" resource is only immutable per-identity - the same
 * URL means a different, owner-scoped resource once the signed-in user
 * changes. `epoch` is bumped by {@link bumpEpoch} on every login, logout,
 * and unexpected 401 (see `AuthProvider`); `set` silently drops a write
 * whose caller captured an older epoch, so a slow response already in
 * flight when identity changes can never populate the cache with another
 * user's data after the fact.
 */
const store = new Map();
let epoch = 0;

/** @param {string} key */
export function get(key) {
  return store.get(key);
}

/** @param {string} key */
export function has(key) {
  return store.has(key);
}

/**
 * @param {string} key
 * @param {unknown} value
 * @param {number} [atEpoch] - the epoch {@link currentEpoch} returned when
 *   the fetch this value came from was started. Omit only for a caller
 *   that cannot race an identity change (e.g. a test). If it no longer
 *   matches the current epoch, the write is dropped.
 */
export function set(key, value, atEpoch) {
  if (atEpoch !== undefined && atEpoch !== epoch) {
    return; // a stale write from before the last login/logout/401 - dropped
  }
  store.set(key, value);
}

export function clear() {
  store.clear();
}

/** The current identity epoch - capture this before starting a fetch that will call {@link set}. */
export function currentEpoch() {
  return epoch;
}

/** Clears the cache and advances the epoch, invalidating any write already in flight from before this call. */
export function bumpEpoch() {
  epoch += 1;
  clear();
}
