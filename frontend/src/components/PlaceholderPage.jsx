import { EmptyState } from './EmptyState.jsx';
import { PageHeader } from './PageHeader.jsx';

/**
 * A minimal placeholder for a route not yet implemented. Never fetches or
 * displays fake data - just a real heading inside the shared layout and an
 * empty-state note that the feature is coming later.
 *
 * @param {object} props
 * @param {string} props.title
 * @param {string} [props.description]
 */
export function PlaceholderPage({ title, description }) {
  return (
    <div>
      <PageHeader title={title} description={description} />
      <EmptyState
        title="Coming in a later batch"
        description="This part of Parallax hasn't been built yet - the Foundation shell, routing, and API layer are already in place for it."
      />
    </div>
  );
}
