const TONES = {
  neutral: 'border-border bg-surface-hover text-ink-secondary',
  accent: 'border-accent/30 bg-accent/10 text-accent',
  success: 'border-success/30 bg-success/10 text-success',
  warning: 'border-warning/30 bg-warning/10 text-warning',
};

export function Badge({ children, tone = 'neutral', className = '' }) {
  return (
    <span
      className={`inline-flex items-center rounded border px-1.5 py-0.5 text-xs font-medium ${TONES[tone]}${className ? ` ${className}` : ''}`}
    >
      {children}
    </span>
  );
}
