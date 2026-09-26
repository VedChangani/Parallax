import { EmptyState } from '../../components/EmptyState.jsx';

export function BacktestRejectionsPage() {
  return (
    <EmptyState
      title="Rejections coming soon"
      description="Orders this run rejected for insufficient cash or zero quantity will be listed here."
    />
  );
}
