/**
 * @template T
 * @typedef {object} DataTableColumn
 * @property {string} key
 * @property {string} header
 * @property {(row: T) => import('react').ReactNode} [render] - defaults to
 *   `row[column.key]`
 * @property {'left' | 'right'} [align] - right-align a numeric column
 */

/**
 * A generic, accessible data table with a financial-data-appropriate
 * treatment: compact rows, crisp separators, a strong header, and
 * right-alignable, tabular-numeral columns for numeric values.
 * Feature-specific tables (trades, rejections, equity curve, ...) are
 * built on top of this, not here.
 *
 * @template T
 * @param {object} props
 * @param {DataTableColumn<T>[]} props.columns
 * @param {T[]} props.rows
 * @param {(row: T) => string | number} props.getRowKey
 * @param {string} [props.caption]
 */
export function DataTable({ columns, rows, getRowKey, caption }) {
  return (
    <div className="overflow-x-auto rounded-md border border-border bg-surface">
      <table className="w-full border-collapse text-left text-sm">
        {caption ? <caption className="sr-only">{caption}</caption> : null}
        <thead>
          <tr className="border-b border-border bg-page/60">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={`px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-ink-muted ${
                  column.align === 'right' ? 'text-right' : 'text-left'
                }`}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((row) => (
            <tr key={getRowKey(row)} className="transition-colors duration-150 hover:bg-surface-hover">
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={`px-4 py-2.5 text-ink ${column.align === 'right' ? 'text-right tabular-nums' : ''}`}
                >
                  {column.render ? column.render(row) : row[column.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
