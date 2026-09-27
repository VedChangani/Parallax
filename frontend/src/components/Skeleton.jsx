/**
 * A single restrained loading placeholder block. Compose several to build
 * a feature-specific skeleton layout (e.g. a table's worth of rows) —
 * this primitive stays generic; the composition belongs to the feature.
 *
 * @param {object} [props]
 * @param {string} [props.className]
 */
export function Skeleton({ className = '' }) {
  return <div aria-hidden="true" className={`animate-pulse rounded bg-surface-hover ${className}`} />;
}
