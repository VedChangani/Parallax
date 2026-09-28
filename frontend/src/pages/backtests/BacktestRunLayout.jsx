import { useParams } from 'react-router';
import { NotFound } from '../../components/NotFound.jsx';
import { BacktestRunWorkspace } from '../../features/backtests/BacktestRunWorkspace.jsx';

/** A valid backtest_run id: a positive integer, no leading zero, no sign, no decimal. */
const RUN_ID_PATTERN = /^[1-9][0-9]*$/;

/**
 * N6 (Phase 9 Batch 1): a non-numeric or otherwise invalid `:runId` route
 * param (e.g. `/backtests/not-a-number`) must never become a request for
 * `/api/backtest-runs/NaN` (or `/0`, `/-1`, ...). The param is validated
 * against the exact shape a real run id can have *before* `Number(runId)`
 * is ever computed or handed to `BacktestRunWorkspace`, so no backend
 * request is issued for an id that could never be real. A valid numeric id
 * is unaffected and behaves exactly as before.
 */
export function BacktestRunLayout() {
  const { runId } = useParams();
  if (!RUN_ID_PATTERN.test(runId ?? '')) {
    return <NotFound />;
  }
  return <BacktestRunWorkspace runId={Number(runId)} />;
}
