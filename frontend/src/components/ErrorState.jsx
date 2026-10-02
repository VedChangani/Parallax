import { Button } from './Button.jsx';

const FORBIDDEN_DETAIL = 'Your security token is missing or has expired. Please reload the page and try again.';

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
