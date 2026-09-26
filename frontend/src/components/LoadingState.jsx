/**
 * @param {object} [props]
 * @param {string} [props.label]
 */
export function LoadingState({ label = 'Loading…' }) {
  return (
    <div role="status" aria-live="polite" className="flex items-center gap-2 text-sm text-ink-muted">
      <span aria-hidden="true" className="h-2 w-2 animate-pulse rounded-full bg-accent" />
      {label}
    </div>
  );
}
