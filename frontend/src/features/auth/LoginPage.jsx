import { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router';
import { useAuth } from '../../auth/AuthContext.js';
import { Button } from '../../components/Button.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';

const inputClasses =
  'mt-1 w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';

const GENERIC_LOGIN_FAILURE = 'Invalid username or password.';

function safeNextPath(value) {
  if (typeof value !== 'string' || value === '') return '/';
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return '/';
  return value;
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    setError('');
    setSubmitting(true);
    try {
      await login(username, password);
      navigate(safeNextPath(searchParams.get('next')), { replace: true });
    } catch {
      setError(GENERIC_LOGIN_FAILURE);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <PageHeader title="Log in" description="Sign in to access your strategies, datasets, and backtests." />
      <form onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="login-username" className="block text-sm font-medium text-ink">
            Username
          </label>
          <input
            id="login-username"
            name="username"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className={inputClasses}
          />
        </div>
        <div className="mt-4">
          <label htmlFor="login-password" className="block text-sm font-medium text-ink">
            Password
          </label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className={inputClasses}
          />
        </div>

        {error ? (
          <p role="alert" className="mt-4 text-sm text-danger">
            {error}
          </p>
        ) : null}

        <div className="mt-6">
          <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </Button>
        </div>
      </form>

      <p className="mt-6 text-sm text-ink-secondary">
        Need an account?{' '}
        <Link to="/register" className="font-medium text-accent hover:underline">
          Register
        </Link>
      </p>
    </div>
  );
}
