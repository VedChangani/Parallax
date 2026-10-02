import { Link } from 'react-router';
import { Badge } from '../../components/Badge.jsx';
import { CopyButton } from '../../components/CopyButton.jsx';
import { DataTable } from '../../components/DataTable.jsx';
import { SOURCE_LABELS } from '../../lib/datasetLabels.js';
import { formatInstantDate } from '../../lib/format.js';

export function DatasetVersionHistory({ datasetId, versions }) {
  const sorted = [...versions].sort((a, b) => b.versionNumber - a.versionNumber);

  return (
    <DataTable
      caption="Data snapshots"
      rows={sorted}
      getRowKey={(row) => row.versionNumber}
      columns={[
        {
          key: 'versionNumber',
          header: 'Snapshot',
          render: (row) => <span className="font-medium text-ink">v{row.versionNumber}</span>,
        },
        {
          key: 'source',
          header: 'Source',
          render: (row) => <Badge tone="accent">{SOURCE_LABELS[row.source] ?? row.source}</Badge>,
        },
        {
          key: 'coverage',
          header: 'Coverage',
          render: (row) => (
            <span className="whitespace-nowrap">
              {row.firstDate} → {row.lastDate}
            </span>
          ),
        },
        { key: 'barCount', header: 'Bars', align: 'right', render: (row) => row.barCount.toLocaleString() },
        { key: 'createdAt', header: 'Created', render: (row) => formatInstantDate(row.createdAt) },
        {
          key: 'contentHash',
          header: 'Hash',
          render: (row) => (
            <div className="flex items-center gap-1.5">
              <span className="truncate font-mono text-xs text-ink-muted" title={row.contentHash}>
                {row.contentHash.slice(0, 10)}…
              </span>
              <CopyButton value={row.contentHash} label="Copy" />
            </div>
          ),
        },
        {
          key: 'open',
          header: '',
          render: (row) => (
            <Link
              to={`/datasets/${datasetId}/versions/${row.versionNumber}`}
              className="whitespace-nowrap font-medium text-accent hover:underline"
            >
              Open snapshot →
            </Link>
          ),
        },
      ]}
    />
  );
}
