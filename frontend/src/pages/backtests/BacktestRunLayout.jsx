import { useParams } from 'react-router';
import { NotFound } from '../../components/NotFound.jsx';
import { BacktestRunWorkspace } from '../../features/backtests/BacktestRunWorkspace.jsx';

const RUN_ID_PATTERN = /^[1-9][0-9]*$/;

export function BacktestRunLayout() {
  const { runId } = useParams();
  if (!RUN_ID_PATTERN.test(runId ?? '')) {
    return <NotFound />;
  }
  return <BacktestRunWorkspace runId={Number(runId)} />;
}
