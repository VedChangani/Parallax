import { Badge } from '../../components/Badge.jsx';
import { DataTable } from '../../components/DataTable.jsx';
import { formatMoney, formatOrDash } from '../../lib/format.js';
import { IndicatorSnapshot } from './IndicatorSnapshot.jsx';

const REASON_LABELS = { ZERO_QUANTITY: 'Zero quantity', INSUFFICIENT_CASH: 'Insufficient cash' };

const COLUMNS = [
  { key: 'seq', header: '#', align: 'right', render: (rejection) => rejection.seq },
  {
    key: 'reason',
    header: 'Reason',
    render: (rejection) => <Badge tone="warning">{REASON_LABELS[rejection.reason] ?? rejection.reason}</Badge>,
  },
  { key: 'executionDate', header: 'Execution date', render: (rejection) => formatOrDash(rejection.executionDate) },
  { key: 'orderId', header: 'Order ID', render: (rejection) => formatOrDash(rejection.orderId) },
  { key: 'quantity', header: 'Quantity', align: 'right', render: (rejection) => formatOrDash(rejection.quantity) },
  { key: 'requiredCash', header: 'Required cash', align: 'right', render: (rejection) => formatMoney(rejection.requiredCash) },
  { key: 'availableCash', header: 'Available cash', align: 'right', render: (rejection) => formatMoney(rejection.availableCash) },
  {
    key: 'signal',
    header: 'Signal',
    render: (rejection) => (
      <IndicatorSnapshot date={rejection.signalDate} close={rejection.signalClose} indicators={rejection.signalIndicators} />
    ),
  },
];

/**
 * The rejected-order table (D-34 Batch 5 §14): fields other than
 * `seq`/`reason`/signal are `null` for a `ZERO_QUANTITY` rejection (no
 * order was ever created) and rendered as "—", never a fabricated value.
 * Rows are shown in the exact engine append order the backend already
 * returns - never re-sorted here.
 *
 * @param {object} props
 * @param {import('../../api/types.js').BacktestRejectionResponse[]} props.rejections
 */
export function RejectionsTable({ rejections }) {
  if (rejections.length === 0) {
    return <p className="text-sm text-ink-secondary">No orders were rejected.</p>;
  }

  return <DataTable columns={COLUMNS} rows={rejections} getRowKey={(rejection) => rejection.seq} caption="Rejections" />;
}
