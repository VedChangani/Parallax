export function PageHeader({ title, description, actions }) {
  return (
    <div className="mb-8 flex items-start justify-between gap-4 border-b border-border pb-6">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight text-ink">{title}</h1>
        {description ? <p className="mt-1.5 text-sm text-ink-secondary">{description}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </div>
  );
}
