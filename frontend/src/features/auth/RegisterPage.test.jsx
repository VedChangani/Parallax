import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
          errors: [{ field: 'username', message: 'username must not contain NUL characters' }],
        },
        400,
      ),
    );

    renderAt();
    submitRegister('Bad Username', 'a-perfectly-fine-password');

    expect(await screen.findByText('username must not contain NUL characters')).toBeTruthy();
  });

  it('shows a field-level error for a weak password (D-38, field: "password")', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(
        { title: 'Malformed request', status: 400, detail: 'password must be at most 72 bytes', field: 'password' },
        400,
      ),
    );

    renderAt();
    submitRegister('gooduser', 'x'.repeat(73));

    expect(await screen.findByText('password must be at most 72 bytes')).toBeTruthy();
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

  describe('client-side validation (D-43)', () => {
    beforeEach(() => {
      globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ username: 'x' }, 201));
    });

    it('requires a username that is non-blank after trimming, without calling the API', () => {
      renderAt();
      submitRegister('   ', 'a-perfectly-fine-password');

      expect(screen.getByText('Username is required.')).toBeTruthy();
      expect(globalThis.fetch).not.toHaveBeenCalled();
    });

    it('rejects a username over 64 characters, counting characters not UTF-16 units', () => {
      renderAt();
      submitRegister('a'.repeat(65), 'a-perfectly-fine-password');
      expect(screen.getByText('Username must be at most 64 characters.')).toBeTruthy();
      expect(globalThis.fetch).not.toHaveBeenCalled();
    });

    it('rejects a password shorter than 8 characters with the 8-character message', () => {
      renderAt();
      submitRegister('Ved', 'x'.repeat(7));

      expect(screen.getByText('Password must be at least 8 characters.')).toBeTruthy();
      expect(globalThis.fetch).not.toHaveBeenCalled();
    });

    it('reports both a bad username and a bad password at once', () => {
      renderAt();
      submitRegister('', '');

      expect(screen.getByText('Username is required.')).toBeTruthy();
      expect(screen.getByText('Password must be at least 8 characters.')).toBeTruthy();
    });

    it.each([
      ['a capitalised name', 'Ved'],
      ['spaces and symbols', 'Ved K. <dev> & co!'],
      ['non-ASCII', 'ünïcödé 名前'],
      ['a single character', 'V'],
      ['exactly 64 characters', 'a'.repeat(64)],
      ['exactly 64 characters made of surrogate pairs', '\u{1F600}'.repeat(64)],
    ])('accepts %s and posts it unchanged', async (_label, username) => {
      renderAt();
      submitRegister(username, 'x'.repeat(8));

      expect(await screen.findByRole('heading', { name: 'Log in' })).toBeTruthy();
      const body = JSON.parse(globalThis.fetch.mock.calls[0][1].body);
      expect(body).toEqual({ username, password: 'x'.repeat(8) });
    });

    it('trims surrounding whitespace from the username before sending it, keeping case', async () => {
      renderAt();
      submitRegister('  Ved  ', 'x'.repeat(8));

      expect(await screen.findByRole('heading', { name: 'Log in' })).toBeTruthy();
      expect(JSON.parse(globalThis.fetch.mock.calls[0][1].body).username).toBe('Ved');
    });

    it('clears the validation errors once the input is corrected and resubmitted', async () => {
      renderAt();
      submitRegister('Ved', 'x'.repeat(7));
      expect(screen.getByText('Password must be at least 8 characters.')).toBeTruthy();

      submitRegister('Ved', 'x'.repeat(8));

      expect(await screen.findByRole('heading', { name: 'Log in' })).toBeTruthy();
    });
  });

  it('navigates to /login on a successful registration without logging in', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ username: 'gooduser' }, 201));

    renderAt();
    submitRegister('gooduser', 'a-perfectly-fine-password');

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeTruthy();
  });
});
