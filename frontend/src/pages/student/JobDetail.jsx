import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ErrorBox, Loader, MatchBreakdown, MatchPill, PageHeader, StatusBadge, SuccessBox, formatDate, requirementText } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { applicationService } from '../../services/applicationService';
import { jobService } from '../../services/jobService';

export default function JobDetail() {
  const { id } = useParams();
  const [job, setJob] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoadError('');
    jobService.get(id).then(setJob).catch((e) => setLoadError(errorMessage(e)));
  }, [id]);
  useEffect(load, [load]);

  async function onApply() {
    setError(''); setSuccess(''); setBusy(true);
    try {
      const app = await applicationService.apply(job.id);
      setJob({ ...job, myApplicationStatus: app.status });
      setSuccess('Application submitted. You can track it under My Applications.');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (loadError) return <ErrorBox message={loadError} onRetry={load} />;
  if (!job) return <Loader />;

  const closed = job.status !== 'OPEN';
  return (
    <>
      <Link to="/student/jobs" className="link small">Back to jobs</Link>
      <PageHeader
        title={job.title}
        subtitle={`${job.companyName}, ${job.location || 'location not set'}`}
        actions={job.myApplicationStatus
          ? <StatusBadge status={job.myApplicationStatus} />
          : <button className="btn btn-primary" onClick={onApply} disabled={busy || closed}>{closed ? 'Closed' : busy ? 'Applying...' : 'Apply now'}</button>}
      />
      <ErrorBox message={error} />
      <SuccessBox message={success} />
      {error.toLowerCase().includes('resume') && (
        <p className="small"><Link className="link" to="/student/profile">Upload your resume on the Profile page</Link></p>
      )}

      <div className="two-col">
        <section className="card">
          <div className="card-head"><h3>Why this score?</h3><MatchPill score={job.match.score} /></div>
          <MatchBreakdown match={job.match} />
          {!job.match.cgpaEligible && <p className="alert alert-warn small">CGPA requirement not met.</p>}
        </section>
        <section className="card facts">
          <h3>Details</h3>
          <dl>
            <dt>Skills asked</dt><dd>{requirementText(job.requirements) || '-'}</dd>
            <dt>Type</dt><dd>{job.jobType || '-'}</dd>
            <dt>Salary</dt><dd>{job.salary || '-'}</dd>
            <dt>Experience</dt><dd>{job.experience || '-'}</dd>
            <dt>Minimum CGPA</dt><dd>{job.minCgpa ?? 'None'}</dd>
            <dt>Openings</dt><dd>{job.openings ?? '-'}</dd>
            <dt>Apply by</dt><dd>{formatDate(job.deadline)}</dd>
          </dl>
        </section>
      </div>

      {job.description && (
        <section className="card">
          <h3>About the role</h3>
          <p className="pre">{job.description}</p>
        </section>
      )}
    </>
  );
}
