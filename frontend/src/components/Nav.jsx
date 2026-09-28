import { NavLink } from 'react-router';
import { useAuth } from '../auth/AuthContext.js';
import { buttonClasses } from './buttonStyles.js';

const LINKS = [
  { to: '/backtests', label: 'Backtests' },
  { to: '/strategies', label: 'Strategies' },
  { to: '/datasets', label: 'Markets' },
];

/**
 * Persistent primary navigation, shared by every page via AppLayout
 * (including the public `/login`/`/register` pages, so the shell never
 * visually diverges between anonymous and authenticated - D-39). The
 * resource links themselves are always shown; an anonymous visitor who
 * clicks one is simply sent to `/login` by `RequireAuth`.
 */
export function Nav() {
  return (
    <header className="border-b border-border bg-surface">
      <nav aria-label="Primary" className="mx-auto flex max-w-6xl items-center gap-8 px-6 py-3.5">
        <span className="flex items-center gap-2">
          <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm bg-accent" />
          <span className="text-base font-semibold tracking-tight text-ink">Parallax</span>
        </span>
        <ul className="flex items-center gap-1">
          {LINKS.map((link) => (
            <li key={link.to}>
              <NavLink
                to={link.to}
                className={({ isActive }) =>
                  `rounded-md px-3 py-2 text-sm font-medium transition-colors duration-150 ` +
                  `focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 ${
                    isActive ? 'bg-accent/10 text-accent' : 'text-ink-muted hover:bg-surface-hover hover:text-ink'
                  }`
                }
              >
                {link.label}
              </NavLink>
            </li>
          ))}
        </ul>
        <div className="ml-auto">
          <NavIdentity />
        </div>
      </nav>
    </header>
  );
}

/** The signed-in username plus a logout button, or a "Log in" link while anonymous. */
function NavIdentity() {
  const { status, username, logout } = useAuth();

  if (status !== 'authenticated') {
    return (
      <NavLink to="/login" className="text-sm font-medium text-ink-muted hover:text-ink">
        Log in
      </NavLink>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <span className="text-sm text-ink-secondary">{username}</span>
      <button type="button" onClick={logout} className={buttonClasses('subtle')}>
        Log out
      </button>
    </div>
  );
}
