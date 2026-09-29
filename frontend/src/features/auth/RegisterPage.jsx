import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { register } from '../../api/auth.js';
import { Button } from '../../components/Button.jsx';
import { FieldError } from '../../components/FieldError.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';

const inputClasses =
  'mt-1 w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-ink ' +
  'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';

// D-43: the only username rules are non-blank (after trimming) and at most 64
// characters (code points, as the backend's varchar(64) counts them); the only
// password rule checked here is the 8-character minimum. The backend stays
// authoritative for everything else (72-byte password maximum, duplicates).
const USERNAME_MAX_LENGTH = 64;
const PASSWORD_MIN_LENGTH = 8;

/**
 * @param {string} username
 * @param {string} password
 * @returns {{username?: string, password?: string}}
 */
function validateRegistration(username, password) {
  const errors = {};
  const trimmed = username.trim();
  if (trimmed.length === 0) {
    errors.username = 'Username is required.';
  } else if ([...trimmed].length > USERNAME_MAX_LENGTH) {
    errors.username = `Username must be at most ${USERNAME_MAX_LENGTH} characters.`;
  }
  if (password.length < PASSWORD_MIN_LENGTH) {
    errors.password = `Password must be at least ${PASSWORD_MIN_LENGTH} characters.`;
  }
  return errors;
}

/**
 * The registration page (D-38/D-39). A successful registration does not
 * log the visitor in - it never calls `AuthProvider`'s `login` - so it
 * sends them to `/login` to sign in with the account they just created,
 * matching the backend's own "register never logs in" contract.
 */
export function RegisterPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    setFormError('');
    const invalid = validateRegistration(username, password);
    if (Object.keys(invalid).length > 0) {
      setFieldErrors(invalid);
      return;
    }
    setFieldErrors({});
    setSubmitting(true);
    try {
      await register(username.trim(), password);
      navigate('/login', { replace: true });
    } catch (error) {
      applyServerError(error, setFieldErrors, setFormError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <PageHeader title="Register" description="Create an account to start building strategies and backtests." />
      <form onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="register-username" className="block text-sm font-medium text-ink">
            Username
          </label>
          <input
            id="register-username"
            name="username"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            aria-invalid={Boolean(fieldErrors.username)}
            aria-describedby={fieldErrors.username ? 'register-username-error' : undefined}
            className={inputClasses}
          />
          <FieldError id="register-username-error" message={fieldErrors.username} />
        </div>
        <div className="mt-4">
          <label htmlFor="register-password" className="block text-sm font-medium text-ink">
            Password
          </label>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'register-password-error' : undefined}
            className={inputClasses}
          />
          <FieldError id="register-password-error" message={fieldErrors.password} />
        </div>

        {formError ? (
          <p role="alert" className="mt-4 text-sm text-danger">
            {formError}
          </p>
        ) : null}

        <div className="mt-6">
          <Button type="submit" variant="primary" disabled={submitting} aria-busy={submitting}>
            {submitting ? 'Creating account…' : 'Register'}
          </Button>
        </div>
      </form>

      <p className="mt-6 text-sm text-ink-secondary">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-accent hover:underline">
          Log in
        </Link>
      </p>
    </div>
  );
}

/**
 * Maps a register `ApiError` onto the form (mirrors {@code
 * StrategyCreateForm}'s `applyServerError`): a 400 Bean Validation failure
 * (bad username) or the D-38 password-policy 400 (`field: "password"`)
 * lands on the matching input; a 409 (duplicate username) or 403
 * (registration disabled) lands as a form-level message.
 */
function applyServerError(error, setFieldErrors, setFormError) {
  if (error.errors?.length) {
    setFieldErrors(Object.fromEntries(error.errors.map((fieldError) => [fieldError.field, fieldError.message])));
    return;
  }
  if (error.field) {
    setFieldErrors({ [error.field]: error.detail ?? error.title ?? 'Invalid value.' });
    return;
  }
  setFormError(error.detail ?? error.title ?? 'Could not create this account.');
}
