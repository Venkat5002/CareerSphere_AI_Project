import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState, ErrorBox, Loader, PageHeader } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { studentService } from '../../services/studentService';
import { actionLabel, levelName, simKey, simLabel } from '../../components/SkillInput';

/**
 * Opportunity Math - the headline feature.
 * Ranks missing skills by how many open jobs they would push above the match threshold,
 * and lets the student simulate learning a combination of skills.
 */
export default function Opportunities() {
  const [opps, setOpps] = useState(null);
  const [error, setError] = useState('');
  const [chosen, setChosen] = useState([]);
  const [sim, setSim] = useState(null);
  const [simError, setSimError] = useState('');

  const load = useCallback(() => {
    setError('');
    studentService.opportunities().then(setOpps).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  useEffect(() => {
    let cancelled = false;
    setSimError('');
    studentService.whatIf(chosen)
      .then((r) => { if (!cancelled) setSim(r); })
      .catch((e) => { if (!cancelled) setSimError(errorMessage(e)); });
    return () => { cancelled = true; };
  }, [chosen]);

  const toggle = (skill) =>
    setChosen((c) => (c.includes(skill) ? c.filter((s) => s !== skill) : [...c, skill]));

  if (error) return <ErrorBox message={error} onRetry={load} />;
  if (!opps) return <Loader />;

  const maxUnlock = Math.max(1, ...opps.map((o) => o.jobsUnlocked));
  const gained = sim ? sim.reachableAfter - sim.reachableBefore : 0;

  return (
    <>
      <PageHeader
        title="Opportunity Math"
        subtitle="Not just what you're missing - which skill opens the most doors, calculated from every open job."
      />

      <section className="card explain">
        <b>How it's calculated.</b> For each skill you don't have, or have below the level a job asks for, CareerSphere
        pretends you learned it (or levelled it up) and re-scores every open job. Must-have skills count 2 points and
        nice-to-have skills 1; a skill below the level asked earns half. A job counts as <i>within reach</i> when you
        score at least {sim?.threshold ?? 70}% and meet its CGPA cutoff. Skills are ranked by how many <i>new</i> jobs
        they bring within reach. This is a transparent, rule-based calculation - no black box.
      </section>

      {opps.length === 0 ? (
        <EmptyState title="Nothing to calculate yet">
          You already have every skill that open jobs ask for, or there are no open jobs. <Link className="link" to="/student/jobs">Browse jobs</Link>
        </EmptyState>
      ) : (
        <div className="two-col wide-left">
          <section className="card">
            <h3>Skills ranked by jobs unlocked</h3>
            <table className="table">
              <thead>
                <tr><th>Step</th><th>New jobs within reach</th><th>Open jobs asking for more</th><th>Simulate</th></tr>
              </thead>
              <tbody>
                {opps.map((o, i) => (
                  <tr key={o.skill} className={i === 0 && o.jobsUnlocked > 0 ? 'highlight' : ''}>
                    <td>
                      <b>{actionLabel(o)}</b>
                      <div className="muted small">
                        {o.category}{o.currentLevel > 0 && <> · you're {levelName(o.currentLevel)} now</>}
                      </div>
                    </td>
                    <td>
                      <div className="bar-cell">
                        <div className="bar"><span style={{ width: `${(o.jobsUnlocked / maxUnlock) * 100}%` }} /></div>
                        <b>+{o.jobsUnlocked}</b>
                      </div>
                      {o.exampleJobs.length > 0 && <div className="muted small">e.g. {o.exampleJobs.join('; ')}</div>}
                    </td>
                    <td>{o.jobsRequiring}</td>
                    <td>
                      <input type="checkbox" checked={chosen.includes(simKey(o))} onChange={() => toggle(simKey(o))}
                        aria-label={`Simulate: ${actionLabel(o)}`} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section className="card simulator">
            <h3>What-if simulator</h3>
            <p className="muted small">Tick skills in the table to see what learning them would change.</p>
            <ErrorBox message={simError} />
            {sim && (
              <>
                <div className="sim-numbers">
                  <div><div className="sim-value">{sim.reachableBefore}</div><div className="muted small">jobs within reach now</div></div>
                  <div className="sim-arrow" aria-hidden="true">to</div>
                  <div><div className="sim-value accent">{sim.reachableAfter}</div><div className="muted small">after learning {chosen.length ? chosen.map(simLabel).join(' + ') : '...'}</div></div>
                </div>
                {chosen.length > 0 && (
                  <p className="sim-summary">
                    {gained > 0
                      ? <>That's <b>+{gained}</b> of {sim.openJobs} open jobs.</>
                      : 'These skills alone would not bring any new job within reach.'}
                  </p>
                )}
                {sim.newlyReachable.length > 0 && (
                  <ul className="sim-jobs">
                    {sim.newlyReachable.map((j) => (
                      <li key={j.jobId}>
                        <Link className="link" to={`/student/jobs/${j.jobId}`}>{j.title}</Link>
                        <span className="muted small">, {j.companyName}: {j.oldScore}% to <b>{j.newScore}%</b></span>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}
