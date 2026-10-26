import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ErrorBox, Loader, MatchPill, PageHeader } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { studentService } from '../../services/studentService';

/** What-if key for a roadmap step: "Docker" (learn it) or "Java:2" (reach a level). */
const keyOf = (s) => (s.fromLevel === 0 && s.toLevel === 1 ? s.skill : `${s.skill}:${s.toLevel}`);

/**
 * Personal Skill Roadmap: the best 1-, 2- and 3-step plans for this student, found by checking every
 * combination, plus the cheapest path to each nearby job.
 */
export default function Roadmap() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const load = useCallback(() => {
    setError('');
    studentService.roadmap().then(setData).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  if (error) return <ErrorBox message={error} onRetry={load} />;
  if (!data) return <Loader label="Checking every combination..." />;

  return (
    <>
      <PageHeader title="Skill roadmap"
        subtitle={`${data.jobsWithinReachNow} of ${data.openJobs} open jobs are within your reach today. These plans add the most jobs for the least effort.`} />

      <section className="card explain"><b>How it's worked out.</b> {data.howItWorks} {data.bundlesChecked.toLocaleString()} plans were checked for you.
        {data.bestSingleSkillGain === 0 && data.bundles.length > 0 && <> No single skill helps you on its own right now, which is exactly when combining two matters.</>}
      </section>

      {data.bundles.length === 0 ? (
        <div className="empty"><h3>No plan of up to 3 steps brings a new job within reach</h3>
          <p className="muted">Check the closest jobs below: some may be held back by a CGPA cutoff rather than skills.</p></div>
      ) : (
        <div className="roadmap-grid">
          {data.bundles.map((b) => (
            <article key={b.size} className={`card roadmap ${b.size === data.bundles[data.bundles.length - 1].size ? 'best' : ''}`}>
              <div className="card-head">
                <h3>{b.size === 1 ? 'One step' : `${b.size} steps`}</h3>
                {b.synergy && <span className="chip-state on" title="These steps unlock jobs that none of them would unlock alone">Works together</span>}
              </div>
              <div className="roadmap-gain"><b className="num">+{b.newJobs}</b> job{b.newJobs === 1 ? '' : 's'} within reach<span className="muted small"> · effort {b.effort} level{b.effort === 1 ? '' : 's'}</span></div>
              <ol className="roadmap-steps">
                {b.steps.map((s, i) => (
                  <li key={s.skill}><span className="step-no">{i + 1}</span><div><b>{s.action}</b>
                    <div className="muted small">{s.jobsWithinReachAfter} jobs within reach after this step</div></div></li>
                ))}
              </ol>
              {b.unlockedJobs.length > 0 && <p className="muted small">Unlocks: {b.unlockedJobs.join('; ')}</p>}
              <button className="btn btn-ghost btn-small" onClick={() => navigate('/student/dashboard', { state: { simulate: b.steps.map(keyOf) } })}>
                Show on my map
              </button>
            </article>
          ))}
        </div>
      )}

      <section className="card">
        <h3>Closest jobs, and exactly what each needs</h3>
        <p className="muted small">The cheapest set of steps that brings each job within reach on its own.</p>
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Job</th><th>Today</th><th>What it takes</th><th className="num">Effort</th></tr></thead>
            <tbody>
              {data.closestJobs.map((p) => (
                <tr key={p.jobId}>
                  <td><Link className="link" to={`/student/jobs/${p.jobId}`}>{p.job}</Link><div className="muted small">{p.company}</div></td>
                  <td><MatchPill score={p.scoreNow} /></td>
                  <td>{p.blocker ? <span className="muted">{p.blocker}</span> : p.actions.join(' + ')}</td>
                  <td className="num">{p.blocker ? '-' : p.effort}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

/** Banner of invitations from recruiters (Hidden Talent). */
export function Invitations() {
  const [list, setList] = useState([]);
  useEffect(() => { studentService.invitations().then(setList).catch(() => {}); }, []);
  const open = list.filter((i) => !i.applied);
  if (!open.length) return null;
  return (
    <section className="pp-next good invites">
      <div className="grow">
        <b>Recruiters invited you to apply</b>
        <ul>
          {open.map((i) => (
            <li key={i.id}><Link className="link" to={`/student/jobs/${i.jobId}`}>{i.job}</Link> at {i.company}
              <span className="muted small"> · {i.score}% match{i.message ? ` · “${i.message}”` : ''}</span></li>
          ))}
        </ul>
      </div>
    </section>
  );
}
