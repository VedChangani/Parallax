import { useParams } from 'react-router';
import { PlaceholderPage } from '../../components/PlaceholderPage.jsx';

export function StrategyDetailPage() {
  const { id } = useParams();
  return <PlaceholderPage title={`Strategy #${id}`} description="Strategy detail and version history." />;
}
