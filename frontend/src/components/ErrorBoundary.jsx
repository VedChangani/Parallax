import { Component } from 'react';
import { Button } from './Button.jsx';

export class ErrorBoundary extends Component {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
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
        <div role="alert" className="flex min-h-[50vh] items-center justify-center px-6">
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
