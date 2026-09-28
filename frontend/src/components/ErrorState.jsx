import { Button } from './Button.jsx';

/**
 * D-39: a 403 is CSRF-shaped (a missing/expired security token, or a
 * `denyAll` path this account was never going to reach) — never a
 * permission the user could resolve by retrying the same click, so the
 * shown message is a reload suggestion instead of the backend's own
 * generic `detail`.
 */
const FORBIDDEN_DETAIL = 'Your security token is missing or has expired. Please reload the page and try again.';

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
  const detail = error?.status === 403 ? FORBIDDEN_DETAIL : error?.detail;

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
