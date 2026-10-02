import { useState } from 'react';
import { getBacktestEquityCsv, getBacktestTradesCsv } from '../../api/backtests.js';
import { Button } from '../../components/Button.jsx';
import { downloadTextFile } from '../../lib/download.js';

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
