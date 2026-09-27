const BASE =
  'inline-flex items-center justify-center gap-2 rounded-md text-sm font-semibold transition-all duration-150 ' +
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ' +
  'disabled:pointer-events-none disabled:opacity-50';

/**
 * The Foundation's reusable button treatment. `primary` carries the
 * restrained offset-shadow/neo-brutalist accent; the others stay flat so
 * that treatment doesn't dilute across every action on the page.
 */
const VARIANTS = {
  primary:
    'border border-ink bg-accent px-4 py-2 text-white shadow-[2px_2px_0_0_var(--color-ink)] ' +
    'hover:-translate-y-0.5 hover:bg-accent-hover hover:shadow-[3px_3px_0_0_var(--color-ink)] ' +
    'active:translate-y-0 active:shadow-[1px_1px_0_0_var(--color-ink)] ' +
    'focus-visible:ring-accent disabled:translate-y-0 disabled:shadow-none',
  secondary:
    'border border-border bg-surface px-4 py-2 text-ink hover:border-ink hover:bg-surface-hover focus-visible:ring-accent',
  subtle: 'px-3 py-1.5 font-medium text-ink-secondary hover:bg-surface-hover hover:text-ink focus-visible:ring-accent',
  destructive:
    'border border-danger bg-surface px-4 py-2 text-danger hover:bg-danger hover:text-white focus-visible:ring-danger',
};

/**
 * Shared classes for anything that must look like a {@link
 * import('./Button.jsx').Button} - a `<button>` itself, or a non-button
 * element (e.g. a `react-router` `Link` used for navigation) that needs
 * the identical visual treatment.
 *
 * @param {'primary' | 'secondary' | 'subtle' | 'destructive'} [variant]
 * @param {string} [className]
 */
export function buttonClasses(variant = 'primary', className = '') {
  return `${BASE} ${VARIANTS[variant]}${className ? ` ${className}` : ''}`;
}
