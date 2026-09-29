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
