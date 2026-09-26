import { useNavigate } from 'react-router';
import { PageHeader } from '../../components/PageHeader.jsx';
import { StrategyCreateForm } from './StrategyCreateForm.jsx';

/** The "New strategy" page: metadata plus the visual Strategy Builder (D-31). */
export function StrategyNewPage() {
  const navigate = useNavigate();

  return (
    <div>
      <PageHeader title="New strategy" description="Define a structured entry/exit rule set and position sizing." />
      <StrategyCreateForm onCreated={(strategy) => navigate(`/strategies/${strategy.id}`)} />
    </div>
  );
}
