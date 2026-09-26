import { useParams } from 'react-router';
import { StrategyVersionPage } from '../../features/strategies/StrategyVersionPage.jsx';

export function StrategyVersionDetailPage() {
  const { id, version } = useParams();
  return <StrategyVersionPage strategyId={Number(id)} versionNumber={Number(version)} />;
}
