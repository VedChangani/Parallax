const store = new Map();
let epoch = 0;

export function get(key) {
  return store.get(key);
}

export function has(key) {
  return store.has(key);
}

export function set(key, value, atEpoch) {
  if (atEpoch !== undefined && atEpoch !== epoch) {
    return;
  }
  store.set(key, value);
}

export function clear() {
  store.clear();
}

export function currentEpoch() {
  return epoch;
}

export function bumpEpoch() {
  epoch += 1;
  clear();
}
