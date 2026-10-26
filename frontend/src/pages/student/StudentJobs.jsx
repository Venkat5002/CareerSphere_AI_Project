import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { EmptyState, ErrorBox, Loader, MatchBreakdown, MatchPill, PageHeader, StatusBadge } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { jobService } from '../../services/jobService';
import { Invitations } from './Roadmap';

export default function StudentJobs() {
  const [jobs, setJobs] = useState(null);
  const [params] = useSearchParams();
  const initial = params.get('q') || '';
  const [q, setQ] = useState(initial);
  const [query, setQuery] = useState(initial);
  // The search box in the top bar opens this page with ?q=...
  useEffect(() => { setQ(initial); setQuery(initial); }, [initial]);
  const [sort, setSort] = useState('match');
  const [error, setError] = useState('');

  useEffect(() => {
    setJobs(null);
    setError('');
    jobService.list({ q: query || undefined, sort }).then(setJobs).catch((e) => setError(errorMessage(e)));
  }, [query, sort]);

  return (
    <>
      <PageHeader title="Jobs" subtitle="Every score shows exactly which required skills you have and which you're missing." />
      <Invitations />

      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setQuery(q.trim()); }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search title, company, location or skill" aria-label="Search jobs" />
        <button className="btn btn-ghost">Search</button>
        <select value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort jobs">
          <option value="match">Best match first</option>
          <option value="newest">Newest first</option>
        </select>
      </form>

      <ErrorBox message={error} />
      {!jobs && !error && <Loader />}
      {jobs && jobs.length === 0 && <EmptyState title="No jobs found">Try a different search.</EmptyState>}

      <div className="job-list">
        {jobs?.map((j) => (
          <Link to={`/student/jobs/${j.id}`} key={j.id} className="card job-card">
            <div className="job-card-head">
              <div>
                <h3>{j.title}</h3>
                <div className="muted small">
                  {[j.companyName, j.location, j.jobType, j.salary].filter(Boolean).join(', ')}
                </div>
              </div>
              <div className="job-card-right">
                <MatchPill score={j.match.score} />
                {j.myApplicationStatus && <StatusBadge status={j.myApplicationStatus} />}
              </div>
            </div>
            <MatchBreakdown match={j.match} />
          </Link>
        ))}
      </div>
    </>
  );
}
