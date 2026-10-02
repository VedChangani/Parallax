import { useState } from 'react';
import { Link } from 'react-router';
import { importAlphaVantage } from '../../api/datasets.js';
import { Badge } from '../../components/Badge.jsx';
import { Button } from '../../components/Button.jsx';

export function AlphaVantageImportForm({ datasetId, onImported }) {
  const [historyDepth, setHistoryDepth] = useState('COMPACT');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    setError('');
    setResult(null);
    setSubmitting(true);
    try {
      const version = await importAlphaVantage(datasetId, historyDepth);
      setResult(version);
      onImported(version);
    } catch (apiError) {
      setError(describeImportError(apiError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="rounded-md border border-border bg-surface p-5 shadow-[2px_2px_0_0_var(--color-border)]">
      <div className="flex items-center gap-2">
        <h3 className="text-sm font-semibold text-ink">Historical data</h3>
        <Badge tone="accent">Recommended</Badge>
      </div>
      <p className="mt-1 text-sm text-ink-secondary">
        Fetch this symbol's daily bars directly from Alpha Vantage and preserve them as a new, immutable data
        snapshot.
      </p>

      <form onSubmit={handleSubmit} className="mt-4 space-y-4">
        <fieldset>
          <legend className="block text-sm font-medium text-ink">History</legend>
          <div className="mt-2 flex gap-4">
            <label className="flex items-center gap-2 text-sm text-ink">
              <input
                type="radio"
                name="historyDepth"
                value="COMPACT"
                checked={historyDepth === 'COMPACT'}
                onChange={() => setHistoryDepth('COMPACT')}
              />
              Compact <span className="text-ink-muted">- recent daily history</span>
            </label>
            <label className="flex items-center gap-2 text-sm text-ink">
              <input
                type="radio"
                name="historyDepth"
                value="FULL"
                checked={historyDepth === 'FULL'}
                onChange={() => setHistoryDepth('FULL')}
              />
              Full <span className="text-ink-muted">- extended history, where the provider permits it</span>
            </label>
          </div>
        </fieldset>

        {error ? (
          <p role="alert" className="text-sm text-danger">
            {error}
          </p>
        ) : null}

        {result ? (
          <p role="status" className="text-sm text-success">
            Loaded snapshot v{result.versionNumber} ({result.barCount} bars).{' '}
            <Link to={`/datasets/${datasetId}/versions/${result.versionNumber}`} className="font-medium underline">
              Open snapshot
            </Link>
          </p>
        ) : null}

        <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
          {submitting ? 'Loading…' : 'Load historical data'}
        </Button>
      </form>
    </div>
  );
}

function describeImportError(error) {
  if (error.kind === 'network') return 'Could not reach the backend. Check your connection and try again.';
  switch (error.status) {
    case 422:
      return error.detail ?? error.title ?? 'Alpha Vantage rejected this request.';
    case 503:
      return error.detail ?? 'Alpha Vantage is temporarily unavailable. Try again shortly.';
    case 502:
      return error.detail ?? 'Alpha Vantage returned an unexpected response.';
    case 500:
      return error.detail ?? 'An internal error occurred while storing this version.';
    default:
      return error.detail ?? error.title ?? 'Could not load historical data from Alpha Vantage.';
  }
}
