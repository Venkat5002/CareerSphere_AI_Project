import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Copilot from './Copilot';
import Icon from './icons';
import Wordmark from './Wordmark';

/** Sidebar menu per role, grouped like a placement portal. [path, label, icon] */
const NAV = {
  STUDENT: [
    ['Overview', [['/student/dashboard', 'Career map', 'map']]],
    ['Placements', [
      ['/student/placement', 'Placement profile', 'id'],
      ['/student/assessments', 'Skill assessments', 'check'],
      ['/student/jobs', 'Job listings', 'briefcase'],
      ['/student/applications', 'My applications', 'list'],
      ['/student/opportunities', 'Opportunity Math', 'sigma'],
      ['/student/roadmap', 'Skill roadmap', 'route'],
    ]],
    ['Growth', [
      ['/student/certifications', 'Certifications', 'award'],
      ['/student/leaderboard', 'Leaderboard', 'trophy'],
    ]],
    ['Account', [['/student/profile', 'Profile details', 'user']]],
  ],
  RECRUITER: [
    ['Hiring', [
      ['/recruiter/dashboard', 'Overview', 'chart'],
      ['/recruiter/jobs', 'My jobs', 'briefcase'],
      ['/recruiter/jobs/new', 'Post a job', 'plus'],
    ]],
    ['Account', [['/recruiter/profile', 'Company', 'building']]],
  ],
  ADMIN: [
    ['Placement cell', [
      ['/admin/dashboard', 'Placement analytics', 'chart'],
      ['/admin/workshops', 'Workshop planner', 'calendar'],
      ['/admin/certificates', 'Verify certificates', 'shield'],
      ['/admin/leaderboard', 'Batch leaderboard', 'trophy'],
    ]],
  ],
};

const ROLE_LABEL = { STUDENT: 'Student', RECRUITER: 'Recruiter', ADMIN: 'Placement cell' };
const wide = () => typeof window !== 'undefined' && window.matchMedia('(min-width: 1281px)').matches;
const initials = (name) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase();

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isStudent = user.role === 'STUDENT';
  const [railOpen, setRailOpen] = useState(isStudent && wide());
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState('');

  useEffect(() => {
    const mq = window.matchMedia('(min-width: 1281px)');
    const on = () => setRailOpen(isStudent && mq.matches);
    mq.addEventListener?.('change', on);
    return () => mq.removeEventListener?.('change', on);
  }, [isStudent]);

  // Close the phone menu after choosing a page.
  useEffect(() => { setMenuOpen(false); }, [location.pathname]);

  function onLogout() {
    logout();
    navigate('/login');
  }

  function onSearch(e) {
    e.preventDefault();
    const q = search.trim();
    navigate(q ? `/student/jobs?q=${encodeURIComponent(q)}` : '/student/jobs');
  }

  return (
    <div className={`app ${menuOpen ? 'menu-open' : ''}`}>
      <aside className="appnav" aria-label="Main menu">
        <Link className="side-brand" to="/">
          <Wordmark />
          <span>
            <b>CareerSphere AI</b>
            <small>CareerSphere Institute of Technology</small>
          </span>
        </Link>
        <nav className="side-nav">
          {NAV[user.role].map(([group, items]) => (
            <div key={group} className="side-group">
              <div className="side-label">{group}</div>
              {items.map(([to, label, icon]) => (
                <NavLink key={to} to={to} end className={({ isActive }) => `side-link${isActive ? ' active' : ''}`}>
                  <Icon name={icon} /> {label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <button className="side-link side-logout" onClick={onLogout}>
          <Icon name="logout" /> Log out
        </button>
      </aside>
      <button className="side-scrim" aria-label="Close menu" onClick={() => setMenuOpen(false)} tabIndex={menuOpen ? 0 : -1} />

      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-btn" aria-label="Open menu" onClick={() => setMenuOpen(true)}>
            <Icon name="menu" size={20} />
          </button>
          {isStudent ? (
            <form className="top-search" onSubmit={onSearch} role="search">
              <Icon name="search" size={16} />
              <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search jobs, companies, skills"
                aria-label="Search jobs" />
            </form>
          ) : <div className="grow" />}
          <div className="who">
            {isStudent && !railOpen && (
              <button className="copilot-toggle" onClick={() => setRailOpen(true)}>
                <span className="spark" aria-hidden="true" /> Ask Copilot
              </button>
            )}
            <div className="who-text">
              <div className="who-name">{user.fullName}</div>
              <div className="who-role">{ROLE_LABEL[user.role]}</div>
            </div>
            <span className="avatar" aria-hidden="true">{initials(user.fullName)}</span>
          </div>
        </header>
        <div className={`shell ${isStudent && railOpen ? 'with-rail' : ''}`}>
          <main className="content">
            <Outlet />
          </main>
          {/* Kept mounted while hidden so the conversation survives page changes. */}
          {isStudent && <Copilot open={railOpen} onClose={() => setRailOpen(false)} />}
        </div>
      </div>
    </div>
  );
}
