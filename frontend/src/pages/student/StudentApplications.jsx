import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState, ErrorBox, Loader, PageHeader, StatusBadge, formatDate } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { applicationService } from '../../services/applicationService';

const STEPS = ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW_SCHEDULED', 'SELECTED'];

/** Placement journey for one application: how far it has progressed. */
function Journey({ status }) {
  const rejected = status === 'REJECTED';
  const reached = rejected ? -1 : STEPS.indexOf(status);
  return (
    <ol className="journey" aria-label="Application progress">
      {STEPS.map((s, i) => (
        <li key={s} className={i <= reached ? 'done' : ''}>{s.replace('_SCHEDULED', '').replace('_', ' ').toLowerCase()}</li>
      ))}
    </ol>
  );
}

export default function StudentApplications() {
  const [apps, setApps] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    applicationService.mine().then(setApps).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  if (error) return <ErrorBox message={error} onRetry={load} />;
  if (!apps) return <Loader />;

  return (
    <>
      <PageHeader title="My Applications" subtitle="Status updates from recruiters appear here." />
      {apps.length === 0 && (
        <EmptyState title="No applications yet">
          <Link className="link" to="/student/jobs">Find a job that matches you</Link>
        </EmptyState>
      )}
      <div className="job-list">
        {apps.map((a) => (
          <div key={a.id} className="card">
            <div className="job-card-head">
              <div>
                <h3><Link to={`/student/jobs/${a.jobId}`} className="plain">{a.jobTitle}</Link></h3>
                <div className="muted small">{a.companyName}. Applied {formatDate(a.appliedAt)}, updated {formatDate(a.updatedAt)}</div>
              </div>
              <StatusBadge status={a.status} />
            </div>
            <Journey status={a.status} />
            {a.recruiterNote && <p className="note"><b>Recruiter note:</b> {a.recruiterNote}</p>}
          </div>
        ))}
      </div>
    </>
  );
}
