import { Button } from './Button.jsx';

/**
 * Displays an ApiError to the user. Distinct from ErrorBoundary: this is
 * for a failed data fetch (a normal, recoverable outcome), not an
 * unexpected rendering crash.
 *
 * @param {object} props
 * @param {import('../api/apiError.js').ApiError} [props.error]
 * @param {string} [props.title]
 * @param {() => void} [props.onRetry]
 */
export function ErrorState({ error, title, onRetry }) {
  const heading = title ?? error?.title ?? 'Something went wrong';
  const detail = error?.detail;

  return (
    <div role="alert" className="rounded-md border border-danger bg-danger-bg p-4">
      <p className="text-sm font-semibold text-danger">{heading}</p>
      {detail ? <p className="mt-1 text-sm text-ink-secondary">{detail}</p> : null}
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry} className="mt-3">
          Retry
        </Button>
      ) : null}
    </div>
  );
}
