import { useState } from 'react';

/**
 * A small button that copies an exact value (a hash, an id) to the
 * clipboard - shown with monospace treatment, matching the visual
 * convention for hashes elsewhere in the app.
 *
 * @param {object} props
 * @param {string} props.value
 * @param {string} [props.label]
 */
export function CopyButton({ value, label = 'Copy' }) {
  const [copied, setCopied] = useState(false);

  async function handleClick() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-label={`${label} ${value}`}
      className="inline-flex items-center gap-1.5 rounded border border-border bg-surface px-2 py-1 font-mono text-xs text-ink-secondary transition-colors duration-150 hover:border-ink hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
    >
      {copied ? 'Copied' : label}
    </button>
  );
}
