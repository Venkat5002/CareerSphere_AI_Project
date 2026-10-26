import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState, ErrorBox, Loader, PageHeader, StatusBadge, formatDate, requirementText } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { recruiterService } from '../../services/recruiterService';

export default function RecruiterJobs() {
  const [jobs, setJobs] = useState(null);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(() => {
    recruiterService.jobs().then(setJobs).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  async function toggleStatus(job) {
    setError(''); setBusyId(job.id);
    try {
      const updated = await recruiterService.setJobStatus(job.id, job.status === 'OPEN' ? 'CLOSED' : 'OPEN');
      setJobs(jobs.map((j) => (j.id === job.id ? { ...j, status: updated.status } : j)));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  async function remove(job) {
    if (!window.confirm(`Delete "${job.title}"? This cannot be undone.`)) return;
    setError(''); setBusyId(job.id);
    try {
      await recruiterService.deleteJob(job.id);
      setJobs(jobs.filter((j) => j.id !== job.id));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  if (!jobs && !error) return <Loader />;

  return (
    <>
      <PageHeader title="My Jobs" actions={<Link className="btn btn-primary" to="/recruiter/jobs/new">Post a job</Link>} />
      <ErrorBox message={error} />
      {jobs?.length === 0 && <EmptyState title="No jobs posted yet">Post your first job to start receiving applicants.</EmptyState>}
      {jobs?.length > 0 && (
        <div className="card table-wrap">
          <table className="table">
            <thead>
              <tr><th>Job</th><th>Status</th><th>Applicants</th><th>Deadline</th><th /></tr>
            </thead>
            <tbody>
              {jobs.map((j) => (
                <tr key={j.id}>
                  <td><b>{j.title}</b><div className="muted small">{j.requirements ? requirementText(j.requirements) : j.skills.join(', ')}</div></td>
                  <td><StatusBadge status={j.status} /></td>
                  <td><Link className="link" to={`/recruiter/jobs/${j.id}/applicants`}>{j.applicantCount} view</Link></td>
                  <td>{formatDate(j.deadline)}</td>
                  <td className="row-actions">
                    <Link className="btn btn-ghost btn-small" to={`/recruiter/jobs/${j.id}/edit`}>Edit</Link>
                    <button className="btn btn-ghost btn-small" disabled={busyId === j.id} onClick={() => toggleStatus(j)}>
                      {j.status === 'OPEN' ? 'Close' : 'Reopen'}
                    </button>
                    <button className="btn btn-ghost btn-small danger" disabled={busyId === j.id} onClick={() => remove(j)}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
