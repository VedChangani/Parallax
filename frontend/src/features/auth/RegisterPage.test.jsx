import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RegisterPage } from './RegisterPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function renderAt(path = '/register') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<h1>Log in</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

function submitRegister(username, password) {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: /register/i }));
}

describe('RegisterPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a field-level error for an invalid username (Bean Validation shape)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          title: 'Malformed request',
          status: 400,
          detail: 'request failed validation',
          errors: [{ field: 'username', message: 'must match "^[a-z0-9][a-z0-9._-]{2,63}$"' }],
        },
        400,
      ),
    );

    renderAt();
    submitRegister('Bad Username', 'a-perfectly-fine-password');

    expect(await screen.findByText('must match "^[a-z0-9][a-z0-9._-]{2,63}$"')).toBeTruthy();
  });

  it('shows a field-level error for a weak password (D-38, field: "password")', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(
        { title: 'Malformed request', status: 400, detail: 'password must be at least 15 characters', field: 'password' },
        400,
      ),
    );

    renderAt();
    submitRegister('gooduser', 'short');

    expect(await screen.findByText('password must be at least 15 characters')).toBeTruthy();
  });

  it('shows a form-level message for a duplicate username (409, no field)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse({ title: 'Conflict', status: 409, detail: 'username gooduser is already taken' }, 409),
    );

    renderAt();
    submitRegister('gooduser', 'a-perfectly-fine-password');

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toBe('username gooduser is already taken');
  });

  it('navigates to /login on a successful registration without logging in', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ username: 'gooduser' }, 201));

    renderAt();
    submitRegister('gooduser', 'a-perfectly-fine-password');

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeTruthy();
  });
});
