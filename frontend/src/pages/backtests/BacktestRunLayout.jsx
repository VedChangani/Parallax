import { Outlet, useParams } from 'react-router';
import { PageHeader } from '../../components/PageHeader.jsx';
import { Tabs } from '../../components/Tabs.jsx';

/**
 * The nested layout for one backtest run: establishes the
 * Overview/Trades/Rejections tab structure now, so later batches can plug
 * real content into each child route without changing the routing
 * architecture. No API data is fetched here yet.
 */
export function BacktestRunLayout() {
  const { runId } = useParams();

  const tabs = [
    { to: `/backtests/${runId}`, label: 'Overview', end: true },
    { to: `/backtests/${runId}/trades`, label: 'Trades' },
    { to: `/backtests/${runId}/rejections`, label: 'Rejections' },
  ];

  return (
    <div>
      <PageHeader title={`Backtest run #${runId}`} description="Run detail, trades, and rejections." />
      <Tabs tabs={tabs} label="Backtest run sections" />
      <Outlet />
    </div>
  );
}
