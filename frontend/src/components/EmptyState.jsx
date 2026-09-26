/**
 * @param {object} props
 * @param {string} props.title
 * @param {string} [props.description]
 * @param {import('react').ReactNode} [props.action] - e.g. a primary CTA
 *   button, or a short list of next steps, shown below the description
 */
export function EmptyState({ title, description, action }) {
  return (
    <div className="rounded-md border border-dashed border-border bg-surface p-10 text-center">
      <p className="text-sm font-semibold text-ink">{title}</p>
      {description ? <p className="mx-auto mt-1.5 max-w-md text-sm text-ink-secondary">{description}</p> : null}
      {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
    </div>
  );
}
