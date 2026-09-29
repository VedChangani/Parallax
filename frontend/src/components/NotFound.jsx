import { EmptyState } from './EmptyState.jsx';
import { PageHeader } from './PageHeader.jsx';

export function NotFound() {
  return (
    <div>
      <PageHeader title="Page not found" description="The page you're looking for doesn't exist." />
      <EmptyState
        title="Nothing here"
        description="Check the URL, or use the navigation above to find your way."
      />
    </div>
  );
}
