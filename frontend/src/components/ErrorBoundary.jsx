import { Component } from 'react';
import { Button } from './Button.jsx';

/**
 * Catches unexpected React rendering errors anywhere below it in the tree
 * and shows a generic, recoverable error screen - never the raw exception
 * message or a stack trace. This is strictly for rendering failures; API
 * errors are surfaced through ApiError/useApiResource, never through this
 * boundary.
 */
export class ErrorBoundary extends Component {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    // Logged for developer diagnosis only - never rendered to the user.
    console.error('Unhandled rendering error', error, info);
  }

  handleRetry = () => {
    this.setState({ hasError: false });
  };

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div role="alert" className="flex min-h-screen items-center justify-center bg-page px-6">
          <div className="max-w-sm rounded-md border border-border bg-surface p-8 text-center">
            <h1 className="text-xl font-semibold tracking-tight text-ink">Something went wrong</h1>
            <p className="mt-2 text-sm text-ink-secondary">
              An unexpected error occurred. You can try again or reload the page.
            </p>
            <div className="mt-6 flex justify-center gap-3">
              <Button variant="primary" onClick={this.handleRetry}>
                Retry
              </Button>
              <Button variant="secondary" onClick={this.handleReload}>
                Reload
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
