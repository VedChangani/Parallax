export function Skeleton({ className = '' }) {
  return <div aria-hidden="true" className={`animate-pulse rounded bg-surface-hover ${className}`} />;
}
