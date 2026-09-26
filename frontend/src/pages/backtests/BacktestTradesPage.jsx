import { EmptyState } from '../../components/EmptyState.jsx';

export function BacktestTradesPage() {
  return (
    <EmptyState
      title="Trades coming soon"
      description="Each closed and open trade derived from this run's fills will be listed here."
    />
  );
}
