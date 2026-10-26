import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorBox, Loader, PageHeader, StatCard, formatDate, statusLabel } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { adminService } from '../../services/adminService';

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    Promise.all([adminService.stats(), adminService.users()])
      .then(([s, u]) => { setStats(s); setUsers(u); })
      .catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  if (error) return <ErrorBox message={error} onRetry={load} />;
  if (!stats) return <Loader />;

  const funnel = Object.entries(stats.applicationsByStatus);
  const maxFunnel = Math.max(1, ...funnel.map(([, n]) => n));

  return (
    <>
      <PageHeader title="Placement Analytics" subtitle="Live numbers from the CareerSphere database." />
      <div className="stat-grid">
        <StatCard label="Students" value={stats.students} />
        <StatCard label="Recruiters" value={stats.recruiters} />
        <StatCard label="Open jobs" value={stats.openJobs} hint={`${stats.totalJobs} total`} />
        <StatCard label="Applications" value={stats.applications} />
        <StatCard label="Placement rate" value={`${stats.placementRatePercent}%`} hint="Students with at least one selection" />
      </div>

      <div className="two-col">
        <section className="card">
          <div className="card-head"><h3>Curriculum vs. market gap</h3><Link className="link small" to="/admin/workshops">Plan workshops</Link></div>
          <p className="muted small">
            Share of open jobs that require a skill, compared with the share of students who have it.
            A big gap is a training opportunity for the placement cell.
          </p>
          {stats.skillGaps.length === 0 ? <p className="muted">No open jobs yet.</p> : (
            <div className="gap-chart" role="table" aria-label="Skill demand versus supply">
              <div className="gap-legend small">
                <span><i className="sw demand" /> Jobs requiring</span>
                <span><i className="sw supply" /> Students having</span>
              </div>
              {stats.skillGaps.map((g) => (
                <div className="gap-row" role="row" key={g.skill}>
                  <div className="gap-skill" role="cell"><b>{g.skill}</b></div>
                  <div className="gap-bars" role="cell">
                    <div className="bar demand"><span style={{ width: `${g.jobDemandPercent}%` }} /><em>{g.jobDemandPercent}%</em></div>
                    <div className="bar supply"><span style={{ width: `${g.studentSupplyPercent}%` }} /><em>{g.studentSupplyPercent}%</em></div>
                  </div>
                  <div className={`gap-n ${g.gap > 0 ? 'pos' : ''}`} role="cell">{g.gap > 0 ? `+${g.gap}` : g.gap}</div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="card">
          <h3>Application funnel</h3>
          <ul className="opp-list">
            {funnel.map(([status, n]) => (
              <li key={status}>
                <div className="opp-label"><b>{statusLabel(status)}</b><span className="muted small">{n}</span></div>
                <div className="bar"><span style={{ width: `${(n / maxFunnel) * 100}%` }} /></div>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="card table-wrap">
        <h3>Users</h3>
        <table className="table">
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Joined</th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}><td>{u.fullName}</td><td>{u.email}</td><td>{u.role}</td><td>{formatDate(u.createdAt)}</td></tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  );
}
