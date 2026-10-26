import { useCallback, useEffect, useState } from 'react';
import { ErrorBox, Loader, PageHeader } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { adminService } from '../../services/adminService';

const GOALS = [
  ['REACH', 'Reach every student', 'Prefer workshops that give students their first job within reach.'],
  ['MATCHES', 'Most matches overall', 'Prefer workshops that create the most new student-job matches.'],
];

/** Invite list for one workshop, with a copy button for emailing students. */
function InviteList({ invite }) {
  const [copied, setCopied] = useState('');
  const text = invite.map((g) => `${g.name} <${g.email}>`).join('\n');

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied('Copied');
    } catch {
      setCopied('Copy failed. Select the names instead.');
    }
  }

  return (
    <div className="invite">
      <table className="table">
        <thead>
          <tr><th>Student</th><th>Branch</th><th className="num">Jobs within reach, now to after the plan</th></tr>
        </thead>
        <tbody>
          {invite.map((g) => (
            <tr key={g.studentId}>
              <td>{g.name}<div className="muted small">{g.email}</div></td>
              <td>{g.branch || '-'}</td>
              <td className="num">
                {g.before} to <b>{g.after}</b>
                {g.before === 0 && <span className="first-tag">first option</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="invite-actions">
        <button type="button" className="btn btn-ghost btn-small" onClick={copy}>Copy invite list</button>
        {copied && <span className="small muted" role="status">{copied}</span>}
      </div>
    </div>
  );
}

/** How this plan compares with choosing one workshop at a time. */
function Comparison({ data }) {
  if (data.method === 'GREEDY') {
    return (
      <p className="muted small">
        There are too many combinations to check them all quickly, so this plan picks one workshop at a time.
      </p>
    );
  }
  const alt = data.oneAtATime;
  if (!alt) return null;
  const sameResult = alt.studentsWithReach === data.studentsWithReachAfterPlan && alt.matches === data.matchesAfterPlan;
  return (
    <p className="compare small">
      Checked all <b className="num">{data.combinationsChecked.toLocaleString('en-IN')}</b> combinations.{' '}
      {sameResult ? (
        <>Picking one workshop at a time would give the same result here.</>
      ) : (
        <>
          Picking one workshop at a time ({alt.skills.join(', ') || 'none'}) would reach{' '}
          <b className="num">{alt.studentsWithReach}</b> students and <b className="num">{alt.matches}</b> matches.
        </>
      )}
    </p>
  );
}

/**
 * Workshop Planner: Opportunity Math for the whole batch.
 * Finds the best combination of skill workshops for the batch and who should attend each one.
 */
export default function WorkshopPlanner() {
  const [goal, setGoal] = useState('REACH');
  const [rounds, setRounds] = useState(3);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(1);

  const load = useCallback(() => {
    setError('');
    adminService.workshops(rounds, goal).then((d) => { setData(d); setOpen(1); }).catch((e) => setError(errorMessage(e)));
  }, [rounds, goal]);
  useEffect(load, [load]);

  if (error && !data) return <ErrorBox message={error} onRetry={load} />;
  if (!data) return <Loader label="Simulating workshops for every student..." />;

  const maxNew = Math.max(1, ...data.ranking.map((k) => k.newMatches));
  const planned = new Set(data.plan.map((p) => p.skill));
  const goalText = GOALS.find((g) => g[0] === goal)[2];

  return (
    <>
      <PageHeader
        title="Workshop planner"
        subtitle={`Which trainings would help this batch most. CareerSphere simulates each skill for all ${data.totalStudents} students against ${data.openJobs} open jobs.`}
      />

      <div className="planner-controls">
        <div className="seg" role="radiogroup" aria-label="Planning goal">
          {GOALS.map(([value, label]) => (
            <button key={value} type="button" role="radio" aria-checked={goal === value}
              className={goal === value ? 'active' : ''} onClick={() => setGoal(value)}>{label}</button>
          ))}
        </div>
        <div className="seg" role="radiogroup" aria-label="Number of workshops">
          {[1, 2, 3, 4].map((n) => (
            <button key={n} type="button" role="radio" aria-checked={rounds === n}
              className={rounds === n ? 'active' : ''} onClick={() => setRounds(n)}>
              {n} workshop{n === 1 ? '' : 's'}
            </button>
          ))}
        </div>
        <p className="muted small">{goalText}</p>
      </div>

      <section className="outcome" aria-live="polite">
        <div>
          <div className="outcome-label">Students with at least one job within reach</div>
          <div className="outcome-line">
            <span className="big num">{data.studentsWithReachNow}</span>
            <span className="muted">now</span>
            <span className="big after num">{data.studentsWithReachAfterPlan}</span>
            <span className="muted">after the plan, of {data.totalStudents}</span>
          </div>
        </div>
        <div>
          <div className="outcome-label">Student-job matches at {data.threshold}% or more</div>
          <div className="outcome-line">
            <span className="big num">{data.matchesNow}</span>
            <span className="muted">now</span>
            <span className="big after num">{data.matchesAfterPlan}</span>
            <span className="muted">after the plan</span>
          </div>
        </div>
      </section>

      <Comparison data={data} />

      <section>
        <h2>The plan</h2>
        <p className="muted small">
          Run these in order. Each student is invited only to the workshops they need, and some skills pay off
          only together, so a workshop's full effect can show up at a later step.
        </p>
        {data.plan.length === 0 ? (
          <p>No workshop would bring any student closer to the open jobs right now.</p>
        ) : (
          <ol className="plan">
            {data.plan.map((p) => (
              <li key={p.order} className={open === p.order ? 'expanded' : ''}>
                <button type="button" className="plan-head" aria-expanded={open === p.order}
                  onClick={() => setOpen(open === p.order ? 0 : p.order)}>
                  <span className="plan-n num">{p.order}</span>
                  <span className="grow">
                    <b className="plan-skill">{p.skill}</b>
                    <span className="muted small">Invite {p.invited} student{p.invited === 1 ? '' : 's'}</span>
                  </span>
                  <span className="plan-stats">
                    <span><b className="num">+{p.firstReach}</b> first option</span>
                    <span><b className="num">+{p.newMatches}</b> matches</span>
                  </span>
                </button>
                {open === p.order && <InviteList invite={p.invite} />}
              </li>
            ))}
          </ol>
        )}
      </section>

      <section className="card table-wrap">
        <h3>Every candidate workshop</h3>
        <p className="muted small">Each row is measured on today's batch, one skill at a time. Workshops in the plan are marked.</p>
        <table className="table">
          <thead>
            <tr>
              <th>Skill</th>
              <th>New matches</th>
              <th className="num">Students helped</th>
              <th className="num">First option</th>
              <th className="num">Open jobs asking</th>
            </tr>
          </thead>
          <tbody>
            {data.ranking.map((k) => (
              <tr key={k.skill} className={planned.has(k.skill) ? 'highlight' : ''}>
                <td><b>{k.skill}</b>{planned.has(k.skill) && <span className="first-tag">in plan</span>}<div className="muted small">{k.category}</div></td>
                <td>
                  <div className="bar-cell">
                    <div className="bar"><span style={{ width: `${(k.newMatches / maxNew) * 100}%` }} /></div>
                    <b className="num">+{k.newMatches}</b>
                  </div>
                </td>
                <td className="num">{k.studentsHelped}</td>
                <td className="num">{k.firstReach}</td>
                <td className="num">{k.openJobsAsking}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="explain card">
        <b>How the plan is chosen.</b> Skills work in combination: a student with only Git gains nothing from a Java
        workshop alone or a Spring Boot workshop alone, but both together bring a Java + Spring Boot + Git job within
        reach. So CareerSphere tries every combination of {rounds} skill{rounds === 1 ? '' : 's'} that open jobs ask for,
        re-scores every student against every open job with the same rules as the Career Map, and keeps the
        combination that best meets the goal. Each student is then invited to the smallest set of those workshops that
        gives them the full benefit. A workshop brings each attendee up to Intermediate, so a student who knows a skill only
        at Beginner level can still gain from it. Picking one workshop at a time is faster but can miss these combinations, so its
        result is shown above for comparison.
      </section>
    </>
  );
}
