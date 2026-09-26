import { useParams } from 'react-router';
import { DatasetDetailPage as DatasetDetailView } from '../../features/datasets/DatasetDetailPage.jsx';

export function DatasetDetailPage() {
  const { id } = useParams();
  return <DatasetDetailView datasetId={Number(id)} />;
}
