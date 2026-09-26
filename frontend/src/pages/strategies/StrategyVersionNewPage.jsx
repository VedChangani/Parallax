import { useParams } from 'react-router';
import { PlaceholderPage } from '../../components/PlaceholderPage.jsx';

export function StrategyVersionNewPage() {
  const { id } = useParams();
  return <PlaceholderPage title={`New version for strategy #${id}`} description="Create a new immutable version." />;
}
