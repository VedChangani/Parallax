import { useParams } from 'react-router';
import { BacktestRunWorkspace } from '../../features/backtests/BacktestRunWorkspace.jsx';

export function BacktestRunLayout() {
  const { runId } = useParams();
  return <BacktestRunWorkspace runId={Number(runId)} />;
}
