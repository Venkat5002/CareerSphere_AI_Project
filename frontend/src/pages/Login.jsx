import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ErrorBox } from '../components/ui';
import AuthSide from '../components/AuthSide';
import { HOME_BY_ROLE, useAuth } from '../context/AuthContext';
import { errorMessage } from '../services/api';

const DEMO = [
  ['Student', 'student@careersphere.ai', 'student123'],
  ['Recruiter', 'recruiter@careersphere.ai', 'recruiter123'],
  ['Admin', 'admin@careersphere.ai', 'admin123'],
];

export default function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(params.get('expired') ? 'Your session expired. Please log in again.' : '');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={HOME_BY_ROLE[user.role]} replace />;

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const u = await login(email, password);
      const from = location.state?.from;
      navigate(from && from.startsWith(`/${u.role.toLowerCase()}`) ? from : HOME_BY_ROLE[u.role], { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <AuthSide
        title="See how close every job really is."
        text="CareerSphere maps each open job by how many of its required skills you have, and shows which one skill would pull the most jobs within reach."
      />
      <div className="auth-card card">
        <h1>Log in</h1>
        <ErrorBox message={error} />
        <form onSubmit={onSubmit} className="form">
          <label>Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
          </label>
          <label>Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Logging in...' : 'Log in'}</button>
        </form>
        <p className="muted center">New here? <Link to="/register">Create an account</Link></p>

        {import.meta.env.DEV && (
          <div className="demo-box">
            <div className="demo-title">Demo accounts (local only)</div>
            {DEMO.map(([label, e, p]) => (
              <button key={e} type="button" className="btn btn-ghost btn-small" onClick={() => { setEmail(e); setPassword(p); }}>
                {label}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
