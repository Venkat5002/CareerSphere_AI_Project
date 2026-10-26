import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { ErrorBox } from '../components/ui';
import AuthSide from '../components/AuthSide';
import { HOME_BY_ROLE, useAuth } from '../context/AuthContext';
import { errorMessage, fieldErrors } from '../services/api';

export default function Register() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'STUDENT' });
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={HOME_BY_ROLE[user.role]} replace />;

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setFields({});
    setBusy(true);
    try {
      const u = await register(form);
      navigate(u.role === 'STUDENT' ? '/student/profile' : '/recruiter/profile', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <AuthSide
        title="Your placement season, on one map."
        text="Profile, resume, matches, applications and recruiter updates in one place, with a Copilot that answers from your own data."
      />
      <div className="auth-card card">
        <h1>Create account</h1>
        <ErrorBox message={error} />
        <form onSubmit={onSubmit} className="form">
          <div className="role-toggle" role="radiogroup" aria-label="Account type">
            {[['STUDENT', "I'm a student"], ['RECRUITER', "I'm a recruiter"]].map(([value, label]) => (
              <button type="button" key={value} role="radio" aria-checked={form.role === value}
                className={form.role === value ? 'active' : ''} onClick={() => setForm({ ...form, role: value })}>
                {label}
              </button>
            ))}
          </div>
          <label>Full name
            <input value={form.fullName} onChange={set('fullName')} required />
            {fields.fullName && <span className="field-error">{fields.fullName}</span>}
          </label>
          <label>Email
            <input type="email" value={form.email} onChange={set('email')} required autoComplete="email" />
            {fields.email && <span className="field-error">{fields.email}</span>}
          </label>
          <label>Password
            <input type="password" value={form.password} onChange={set('password')} required minLength={6} autoComplete="new-password" />
            {fields.password && <span className="field-error">{fields.password}</span>}
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating...' : 'Create account'}</button>
        </form>
        <p className="muted center">Already registered? <Link to="/login">Log in</Link></p>
      </div>
    </div>
  );
}
