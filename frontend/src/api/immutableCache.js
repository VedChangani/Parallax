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
 */
const store = new Map();

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
 */
export function set(key, value) {
  store.set(key, value);
}

export function clear() {
  store.clear();
}
