import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { uploadCsv } from '../../api/datasets.js';
import { Button } from '../../components/Button.jsx';
import { FieldError } from '../../components/FieldError.jsx';

const ADJUSTMENT_BASIS_OPTIONS = [
  { value: 'RAW', label: 'Raw' },
  { value: 'SPLIT_ADJUSTED', label: 'Split-adjusted' },
  { value: 'SPLIT_AND_DIVIDEND_ADJUSTED', label: 'Split & dividend-adjusted' },
];

const selectClasses =
  'mt-1 w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';

/**
 * The CSV import tool panel on the Dataset detail page (D-32
 * `POST /api/datasets/{id}/versions`, multipart).
 *
 * @param {object} props
 * @param {number} props.datasetId
 * @param {(version: import('../../api/types.js').DatasetVersionResponse) => void} props.onImported
 */
export function CsvUploadForm({ datasetId, onImported }) {
  const fileInputRef = useRef(null);
  const [adjustmentBasis, setAdjustmentBasis] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    setError(null);
    setResult(null);

    const file = fileInputRef.current?.files?.[0];
    if (!file) {
      setError({ field: 'file', message: 'Choose a CSV file to upload.' });
      return;
    }
    if (!adjustmentBasis) {
      setError({ field: 'adjustmentBasis', message: 'Select an adjustment basis.' });
      return;
    }

    setSubmitting(true);
    try {
      const version = await uploadCsv(datasetId, file, adjustmentBasis);
      setResult(version);
      if (fileInputRef.current) fileInputRef.current.value = '';
      setAdjustmentBasis('');
      onImported(version);
    } catch (apiError) {
      setError({ field: null, message: describeUploadError(apiError) });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="rounded-md border border-border bg-surface p-5">
      <h3 className="text-sm font-semibold text-ink">Import from CSV</h3>
      <p className="mt-1 text-sm text-ink-secondary">
        Upload a <code className="font-mono text-xs">date,open,high,low,close,volume</code> file to create a new,
        immutable version.
      </p>

      <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-4">
        <div>
          <label htmlFor="csv-file" className="block text-sm font-medium text-ink">
            CSV file
          </label>
          <input
            id="csv-file"
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            aria-invalid={error?.field === 'file'}
            aria-describedby={error?.field === 'file' ? 'csv-file-error' : undefined}
            className="mt-1 block w-full text-sm text-ink-secondary file:mr-3 file:rounded-md file:border file:border-border file:bg-surface-hover file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-ink"
          />
          {error?.field === 'file' ? <FieldError id="csv-file-error" message={error.message} /> : null}
        </div>

        <div>
          <label htmlFor="csv-adjustment-basis" className="block text-sm font-medium text-ink">
            Adjustment basis
          </label>
          <select
            id="csv-adjustment-basis"
            value={adjustmentBasis}
            onChange={(event) => setAdjustmentBasis(event.target.value)}
            aria-invalid={error?.field === 'adjustmentBasis'}
            aria-describedby={error?.field === 'adjustmentBasis' ? 'csv-adjustment-basis-error' : undefined}
            className={selectClasses}
          >
            <option value="" disabled>
              Select adjustment basis…
            </option>
            {ADJUSTMENT_BASIS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-ink-muted">
            An uploader-declared label only - Parallax performs no adjustment calculation itself.
          </p>
          {error?.field === 'adjustmentBasis' ? (
            <FieldError id="csv-adjustment-basis-error" message={error.message} />
          ) : null}
        </div>

        {error && !error.field ? (
          <p role="alert" className="text-sm text-danger">
            {error.message}
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
          {submitting ? 'Uploading…' : 'Upload CSV'}
        </Button>
      </form>
    </div>
  );
}

function describeUploadError(error) {
  if (error.kind === 'network') return 'Could not reach the backend. Check your connection and try again.';
  switch (error.status) {
    case 413:
      return 'This CSV file is too large.';
    case 422:
      return error.detail ?? 'The CSV data is invalid.';
    case 500:
      return error.detail ?? 'An internal error occurred while storing this version.';
    default:
      return error.detail ?? error.title ?? 'Could not upload this CSV file.';
  }
}
