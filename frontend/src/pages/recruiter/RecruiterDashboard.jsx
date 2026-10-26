import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorBox, Loader, PageHeader, StatCard, statusLabel } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { recruiterService } from '../../services/recruiterService';

export default function RecruiterDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    recruiterService.dashboard().then(setData).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  if (error) return <ErrorBox message={error} onRetry={load} />;
  if (!data) return <Loader />;

  const s = data.applicantsByStatus;
  return (
    <>
      <PageHeader
        title={data.companyName || 'Recruiter dashboard'}
        subtitle={data.companyName ? 'Recruiter dashboard' : 'Add your company name in Company Profile.'}
        actions={<Link className="btn btn-primary" to="/recruiter/jobs/new">Post a job</Link>}
      />
      <div className="stat-grid">
        <StatCard label="Active jobs" value={data.activeJobs} hint={`${data.totalJobs} posted in total`} />
        <StatCard label="Applicants" value={data.totalApplicants} hint={`${s.APPLIED} new, ${s.UNDER_REVIEW} under review`} />
        <StatCard label="Shortlisted" value={s.SHORTLISTED} />
        <StatCard label="Interviews" value={s.INTERVIEW_SCHEDULED} />
        <StatCard label="Selected" value={s.SELECTED} />
      </div>
      <section className="card">
        <div className="card-head"><h3>Pipeline</h3><Link className="link" to="/recruiter/jobs">Manage jobs</Link></div>
        <div className="pipeline">
          {Object.entries(s).map(([status, n]) => (
            <div key={status} className="pipeline-step">
              <div className="pipeline-n">{n}</div>
              <div className="muted small">{statusLabel(status)}</div>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
