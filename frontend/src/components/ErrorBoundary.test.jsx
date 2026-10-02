import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary.jsx';

function Bomb() {
  throw new Error('super secret internal stack trace detail');
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a generic recoverable error screen instead of crashing', () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.getByText('Something went wrong')).toBeTruthy();
    expect(screen.getByRole('button', { name: /retry/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /reload/i })).toBeTruthy();
  });

  it('never exposes the raw exception message', () => {
    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );

    expect(screen.queryByText(/super secret internal stack trace detail/i)).toBeNull();
  });

  it('renders children normally when nothing throws', () => {
    render(
      <ErrorBoundary>
        <p>all good</p>
      </ErrorBoundary>,
    );

    expect(screen.getByText('all good')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
