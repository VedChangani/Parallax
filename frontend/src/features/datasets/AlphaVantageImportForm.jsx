import { useState } from 'react';
import { Link } from 'react-router';
import { importAlphaVantage } from '../../api/datasets.js';
import { Button } from '../../components/Button.jsx';

/**
 * The Alpha Vantage import tool panel on the Dataset detail page (D-33
 * `POST /api/datasets/{id}/versions/alpha-vantage`). The API key is
 * backend configuration only - no key field exists here, and none is ever
 * sent in the request body.
 *
 * @param {object} props
 * @param {number} props.datasetId
 * @param {(version: import('../../api/types.js').DatasetVersionResponse) => void} props.onImported
 */
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
    <div className="rounded-md border border-border bg-surface p-5">
      <h3 className="text-sm font-semibold text-ink">Import from Alpha Vantage</h3>
      <p className="mt-1 text-sm text-ink-secondary">
        Fetch this symbol's daily bars directly from Alpha Vantage and create a new, immutable version.
      </p>

      <form onSubmit={handleSubmit} className="mt-4 space-y-4">
        <fieldset>
          <legend className="block text-sm font-medium text-ink">History depth</legend>
          <div className="mt-2 flex gap-4">
            <label className="flex items-center gap-2 text-sm text-ink">
              <input
                type="radio"
                name="historyDepth"
                value="COMPACT"
                checked={historyDepth === 'COMPACT'}
                onChange={() => setHistoryDepth('COMPACT')}
              />
              Compact <span className="text-ink-muted">(recent history)</span>
            </label>
            <label className="flex items-center gap-2 text-sm text-ink">
              <input
                type="radio"
                name="historyDepth"
                value="FULL"
                checked={historyDepth === 'FULL'}
                onChange={() => setHistoryDepth('FULL')}
              />
              Full <span className="text-ink-muted">(entire available history)</span>
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
            Created version {result.versionNumber} ({result.barCount} bars).{' '}
            <Link to={`/datasets/${datasetId}/versions/${result.versionNumber}`} className="font-medium underline">
              View version
            </Link>
          </p>
        ) : null}

        <Button type="submit" variant="secondary" disabled={submitting} aria-busy={submitting}>
          {submitting ? 'Importing…' : 'Import from Alpha Vantage'}
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
      return error.detail ?? error.title ?? 'Could not import from Alpha Vantage.';
  }
}
