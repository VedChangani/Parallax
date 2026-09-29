import { useState } from 'react';
import { getBacktestEquityCsv, getBacktestTradesCsv } from '../../api/backtests.js';
import { Button } from '../../components/Button.jsx';
import { downloadTextFile } from '../../lib/download.js';

/**
 * Two low-emphasis download actions for a completed run (D-42): the equity
 * curve (with drawdown) and the trades, each its own CSV. The file content
 * is fetched from the backend's verified result at click time - never
 * re-derived here and never a backtest re-run - and saved unchanged.
 * A failure shows inline and leaves the rest of the page untouched.
 *
 * @param {object} props
 * @param {number} props.runId
 */
export function ExportActions({ runId }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function exportCsv(fetchCsv, filename) {
    setBusy(true);
    setError(null);
    try {
      downloadTextFile(filename, await fetchCsv(runId));
    } catch (failure) {
      setError(failure?.message ? `Export failed: ${failure.message}` : 'Export failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <div role="group" aria-label="Export results" className="flex items-center gap-1">
        <Button
          variant="subtle"
          disabled={busy}
          onClick={() => exportCsv(getBacktestEquityCsv, `backtest-${runId}-equity-curve.csv`)}
        >
          Export equity CSV
        </Button>
        <Button
          variant="subtle"
          disabled={busy}
          onClick={() => exportCsv(getBacktestTradesCsv, `backtest-${runId}-trades.csv`)}
        >
          Export trades CSV
        </Button>
      </div>
      {error ? (
        <p role="alert" className="max-w-xs text-right text-xs text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
