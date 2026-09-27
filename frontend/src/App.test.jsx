import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App.jsx';

describe('App', () => {
  it('renders the full shell and redirects to /backtests by default', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Backtests' })).toBeTruthy();
    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeTruthy();
    expect(screen.getByRole('main')).toBeTruthy();
  });
});
