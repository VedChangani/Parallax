import { useParams } from 'react-router';
import { StrategyDetailPage as StrategyDetailView } from '../../features/strategies/StrategyDetailPage.jsx';

export function StrategyDetailPage() {
  const { id } = useParams();
  return <StrategyDetailView strategyId={Number(id)} />;
}
