import { useParams } from 'react-router';
import { DatasetVersionPage } from '../../features/datasets/DatasetVersionPage.jsx';

export function DatasetVersionDetailPage() {
  const { id, version } = useParams();
  return <DatasetVersionPage datasetId={Number(id)} versionNumber={Number(version)} />;
}
