import { NavLink } from 'react-router';

export function Tabs({ tabs, label = 'Sections' }) {
  return (
    <div role="tablist" aria-label={label} className="mb-6 flex gap-6 border-b border-border">
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          role="tab"
          className={({ isActive }) =>
            `-mb-px rounded-t-sm border-b-2 px-1 py-2.5 text-sm font-medium transition-colors duration-150 ` +
            `focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 ${
              isActive ? 'border-accent text-ink' : 'border-transparent text-ink-muted hover:border-border hover:text-ink'
            }`
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </div>
  );
}
