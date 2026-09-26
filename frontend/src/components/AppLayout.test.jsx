import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AppLayout } from './AppLayout.jsx';

function renderLayoutAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/backtests" element={<p>backtests page</p>} />
          <Route path="/strategies" element={<p>strategies page</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppLayout', () => {
  it('renders persistent navigation exposing Backtests, Strategies, and Datasets', () => {
    renderLayoutAt('/backtests');

    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Backtests' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Strategies' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Datasets' })).toBeTruthy();
  });

  it('exposes a main landmark containing the routed page', () => {
    renderLayoutAt('/backtests');

    const main = screen.getByRole('main');
    expect(main).toBeTruthy();
    expect(screen.getByText('backtests page')).toBeTruthy();
  });

  it('marks the active nav link with aria-current', () => {
    renderLayoutAt('/strategies');

    expect(screen.getByRole('link', { name: 'Strategies' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getByRole('link', { name: 'Backtests' }).getAttribute('aria-current')).toBeNull();
  });
});
