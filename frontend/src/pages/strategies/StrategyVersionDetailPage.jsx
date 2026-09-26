import { useParams } from 'react-router';
import { PlaceholderPage } from '../../components/PlaceholderPage.jsx';

export function StrategyVersionDetailPage() {
  const { id, version } = useParams();
  return (
    <PlaceholderPage
      title={`Strategy #${id} — version ${version}`}
      description="This version's definition and hash."
    />
  );
}
