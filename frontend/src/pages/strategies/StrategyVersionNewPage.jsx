import { useParams } from 'react-router';
import { StrategyVersionNewPage as StrategyVersionNewPageView } from '../../features/strategies/StrategyVersionNewPage.jsx';

export function StrategyVersionNewPage() {
  const { id } = useParams();
  return <StrategyVersionNewPageView strategyId={Number(id)} />;
}
