import { Link } from 'react-router';
import { Badge } from '../../components/Badge.jsx';
import { CopyButton } from '../../components/CopyButton.jsx';
import { DataTable } from '../../components/DataTable.jsx';

const SOURCE_LABELS = {
  CSV_UPLOAD: 'CSV upload',
  ALPHA_VANTAGE: 'Alpha Vantage',
};

const ADJUSTMENT_BASIS_LABELS = {
  RAW: 'Raw',
  SPLIT_ADJUSTED: 'Split-adjusted',
  SPLIT_AND_DIVIDEND_ADJUSTED: 'Split & dividend-adjusted',
};

/**
 * The version-history table on the Dataset detail page - every field the
 * backend actually returns for a DatasetVersion (D-32), newest first.
 *
 * @param {object} props
 * @param {number} props.datasetId
 * @param {import('../../api/types.js').DatasetVersionResponse[]} props.versions
 */
export function DatasetVersionHistory({ datasetId, versions }) {
  const sorted = [...versions].sort((a, b) => b.versionNumber - a.versionNumber);

  return (
    <DataTable
      caption="Dataset version history"
      rows={sorted}
      getRowKey={(row) => row.versionNumber}
      columns={[
        {
          key: 'versionNumber',
          header: 'Version',
          render: (row) => (
            <Link
              to={`/datasets/${datasetId}/versions/${row.versionNumber}`}
              className="font-medium text-ink hover:text-accent"
            >
              v{row.versionNumber}
            </Link>
          ),
        },
        {
          key: 'source',
          header: 'Source',
          render: (row) => <Badge tone="accent">{SOURCE_LABELS[row.source] ?? row.source}</Badge>,
        },
        {
          key: 'sourceDetail',
          header: 'Source detail',
          render: (row) => <span className="text-ink-secondary">{row.sourceDetail}</span>,
        },
        {
          key: 'adjustmentBasis',
          header: 'Adjustment basis',
          render: (row) => <Badge>{ADJUSTMENT_BASIS_LABELS[row.adjustmentBasis] ?? row.adjustmentBasis}</Badge>,
        },
        { key: 'barCount', header: 'Bars', align: 'right', render: (row) => row.barCount },
        { key: 'firstDate', header: 'First date', render: (row) => row.firstDate },
        { key: 'lastDate', header: 'Last date', render: (row) => row.lastDate },
        {
          key: 'contentHash',
          header: 'Content hash',
          render: (row) => (
            <div className="flex items-center gap-1.5">
              <span className="truncate font-mono text-xs text-ink-muted" title={row.contentHash}>
                {row.contentHash.slice(0, 12)}…
              </span>
              <CopyButton value={row.contentHash} label="Copy hash" />
            </div>
          ),
        },
      ]}
    />
  );
}
