import { Link } from 'react-router';
import { CopyButton } from '../../components/CopyButton.jsx';
import { DataTable } from '../../components/DataTable.jsx';
import { formatInstantDate } from '../../lib/format.js';

/**
 * The version history table for a strategy - every {@link
 * import('../../api/types.js').StrategyVersionSummaryResponse} field shown
 * directly, nothing recomputed.
 *
 * @param {object} props
 * @param {number} props.strategyId
 * @param {import('../../api/types.js').StrategyVersionSummaryResponse[]} props.versions
 */
export function StrategyVersionHistory({ strategyId, versions }) {
  const sorted = [...versions].sort((a, b) => b.versionNumber - a.versionNumber);

  return (
    <DataTable
      caption="Version history"
      rows={sorted}
      getRowKey={(row) => row.versionNumber}
      columns={[
        {
          key: 'versionNumber',
          header: 'Version',
          render: (row) => <span className="font-medium text-ink">v{row.versionNumber}</span>,
        },
        { key: 'createdAt', header: 'Created', render: (row) => formatInstantDate(row.createdAt) },
        {
          key: 'definitionHash',
          header: 'Hash',
          render: (row) => (
            <div className="flex items-center gap-1.5">
              <span className="truncate font-mono text-xs text-ink-muted" title={row.definitionHash}>
                {row.definitionHash.slice(0, 10)}…
              </span>
              <CopyButton value={row.definitionHash} label="Copy" />
            </div>
          ),
        },
        {
          key: 'open',
          header: '',
          render: (row) => (
            <Link
              to={`/strategies/${strategyId}/versions/${row.versionNumber}`}
              className="whitespace-nowrap font-medium text-accent hover:underline"
            >
              Open version →
            </Link>
          ),
        },
      ]}
    />
  );
}
