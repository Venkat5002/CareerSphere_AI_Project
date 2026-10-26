import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ErrorBox, Loader, MatchBreakdown, MatchPill, StatusBadge } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { jobService } from '../../services/jobService';
import { studentService } from '../../services/studentService';
import { actionLabel, simKey, simLabel } from '../../components/SkillInput';

/* Map geometry: the student sits at the center. A job's distance from the center
   is (100 - match score), so a 100% match touches the student and 0% sits on the edge. */
const C = 300;          // center of the 600 x 600 drawing
const R_IN = 62;        // radius of the 100% ring, just outside the student disc
const R_OUT = 250;      // outer edge = 0% match
const radius = (score) => R_IN + (1 - score / 100) * (R_OUT - R_IN);
const short = (t) => (t.length > 24 ? `${t.slice(0, 23)}...` : t);

const ACTION_BUTTON = {
  COMPLETE_PROFILE: 'Complete profile',
  UPLOAD_RESUME: 'Upload resume',
  ADD_SKILLS: 'Add skills',
  GIVE_CONSENT: 'Open placement profile',
  APPLY: 'Open job',
  LEARN_SKILL: 'Try it on the map',
  EXPLORE: 'Browse jobs',
};

export default function CareerMap() {
  const location = useLocation();
  const [dash, setDash] = useState(null);
  const [jobs, setJobs] = useState(null);
  const [opps, setOpps] = useState([]);
  const [error, setError] = useState('');
  const [levers, setLevers] = useState([]);   // skills the student is "trying on"
  const [sim, setSim] = useState(null);       // what-if result for those skills
  const [selectedId, setSelectedId] = useState(null);

  const load = useCallback(() => {
    setError('');
    Promise.all([studentService.dashboard(), jobService.list({ sort: 'match' }), studentService.opportunities()])
      .then(([d, j, o]) => { setDash(d); setJobs(j); setOpps(o); })
      .catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  // The Copilot can open the map with skills to simulate.
  useEffect(() => {
    const wanted = location.state?.simulate;
    if (Array.isArray(wanted) && wanted.length) setLevers(wanted);
  }, [location.state]);

  useEffect(() => {
    if (!levers.length) { setSim(null); return; }
    let cancelled = false;
    studentService.whatIf(levers)
      .then((r) => { if (!cancelled) setSim(r); })
      .catch((e) => { if (!cancelled) setError(errorMessage(e)); });
    return () => { cancelled = true; };
  }, [levers]);

  const simById = useMemo(() => Object.fromEntries((sim?.jobs || []).map((s) => [s.jobId, s])), [sim]);

  // Stable bearings: a job keeps its angle when scores change, so movement reads as "closer" or "further".
  // Jobs are dealt around the circle in score order with a stride, so similar scores (which sit at similar
  // distances) land far apart and their labels don't collide. The top bearing stays free for ring labels.
  const placed = useMemo(() => {
    if (!jobs) return [];
    const n = jobs.length;
    const ordered = [...jobs].sort((a, b) => b.match.score - a.match.score || a.id - b.id);
    const gcd = (a, b) => (b ? gcd(b, a % b) : a);
    let stride = Math.max(1, Math.round(n * 0.382));
    while (n > 1 && gcd(stride, n) !== 1) stride++;
    return ordered.map((j, k) => {
      const slot = (k * stride) % n;
      const angle = (-90 + (360 / n) * (slot + 0.5)) * (Math.PI / 180);
      const s = simById[j.id];
      const score = s ? s.newScore : j.match.score;
      const reach = s ? s.reachableAfter : j.match.score >= 70 && j.match.cgpaEligible;
      const r = radius(score);
      return {
        job: j, angle, score, reach, moved: s && s.newScore !== s.oldScore,
        x: C + r * Math.cos(angle), y: C + r * Math.sin(angle),
        ox: C + radius(j.match.score) * Math.cos(angle), oy: C + radius(j.match.score) * Math.sin(angle),
      };
    });
  }, [jobs, simById]);

  // Label placement: try a few spots next to each dot and keep the first that doesn't overlap
  // a label already placed (inner jobs first, since they're the ones that matter most).
  const labels = useMemo(() => {
    const boxes = [
      { x: C - 42, y: C - 42, w: 84, h: 84 },                                   // the "You" disc
      ...placed.map((p) => ({ x: p.x - 9, y: p.y - 9, w: 18, h: 18 })),          // every dot
    ];
    const result = {};
    const hit = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
    [...placed].sort((a, b) => b.score - a.score).forEach((p) => {
      const text = short(p.job.title);
      const w = text.length * 6.4;
      const right = Math.cos(p.angle) >= -0.01;
      const tries = [[right, 4], [right, -12], [right, 20], [!right, 4], [!right, -12], [!right, 20], [right, -26], [right, 34]];
      let pick = null;
      for (const t of tries) {
        const box = { x: t[0] ? p.x + 12 : p.x - 12 - w, y: p.y + t[1] - 11, w, h: 14 };
        if (!boxes.some((b) => hit(b, box))) { pick = t; boxes.push(box); break; }
      }
      // No free spot: hide the label; the full title still shows on hover and to screen readers.
      result[p.job.id] = pick ? { text, right: pick[0], dy: pick[1] } : null;
    });
    return result;
  }, [placed]);

  if (error && !dash) return <ErrorBox message={error} onRetry={load} />;
  if (!dash || !jobs) return <Loader label="Drawing your map..." />;

  const firstName = dash.fullName.split(' ')[0];
  const action = dash.nextBestAction;
  const actionLink = action.type === 'LEARN_SKILL' ? null : action.link;
  // A lever is a what-if key: "Docker" (learn it) or "Java:2" (level up to Intermediate).
  const leverList = [...new Set([...opps.filter((o) => o.jobsRequiring > 0).slice(0, 6).map(simKey), ...levers])];
  const oppBySkill = Object.fromEntries(opps.map((o) => [simKey(o), o]));
  const selected = placed.find((p) => p.job.id === selectedId);
  const toggle = (skill) => setLevers((l) => (l.includes(skill) ? l.filter((s) => s !== skill) : [...l, skill]));

  return (
    <>
      <section className="map-hello">
        <h1>{firstName}, {dash.jobsAboveThreshold} of {dash.openJobs} open jobs are within your reach.</h1>
        <p className="muted">
          Each point is an open job. The closer it is to you, the more of its required skills you have.
          Jobs inside the saffron ring are at {dash.matchThreshold}% or more.
        </p>
      </section>

      <div className="next-move">
        <div className="grow">
          <b>Next move: {action.title}</b>
          <p>{action.reason}</p>
        </div>
        {actionLink
          ? <Link className="btn btn-primary" to={actionLink}>{ACTION_BUTTON[action.type] || 'Go'}</Link>
          : <button className="btn btn-primary" onClick={() => setLevers([simKey(dash.opportunities[0])])}>{ACTION_BUTTON[action.type]}</button>}
      </div>

      <div className="map-layout">
        <div className="map-stage">
          <svg className="map-svg" viewBox="-80 -10 760 620" role="img"
            aria-label={`Career map with ${jobs.length} open jobs. ${placed.filter((p) => p.reach).length} are within reach.`}>
            {[0, 25, 55, 85].map((s) => <circle key={s} className="contour" cx={C} cy={C} r={radius(s)} />)}
            <circle className="reach-ring" cx={C} cy={C} r={radius(70)} />
            <text className="ring-label reach" x={C} y={C - radius(70) - 7} textAnchor="middle">70% within reach</text>
            <text className="ring-label" x={C} y={C - radius(40) - 6} textAnchor="middle">40%</text>
            <text className="ring-label" x={C} y={C - radius(0) - 6} textAnchor="middle">0%</text>
            <circle className="contour" cx={C} cy={C} r={radius(40)} strokeDasharray="4 5" />

            {sim && placed.filter((p) => p.moved).map((p) => (
              <g key={`t${p.job.id}`}>
                <line className="trail" x1={p.ox} y1={p.oy} x2={p.x} y2={p.y} />
                <circle className="ghost" cx={p.ox} cy={p.oy} r="6" />
              </g>
            ))}

            <circle className="me" cx={C} cy={C} r={R_IN - 8} />
            <text className="me-text" x={C} y={C + 5} textAnchor="middle">You</text>

            {placed.map((p) => {
              const label = labels[p.job.id];
              const cls = ['node', p.reach && 'reach', !p.job.match.cgpaEligible && 'blocked',
                p.job.myApplicationStatus && 'applied', p.job.id === selectedId && 'selected'].filter(Boolean).join(' ');
              return (
                <g key={p.job.id} className={cls} style={{ transform: `translate(${p.x}px, ${p.y}px)` }}
                  tabIndex={0} role="button"
                  aria-label={`${p.job.title} at ${p.job.companyName}, ${p.score}% match${p.reach ? ', within reach' : ''}`}
                  onClick={() => setSelectedId(p.job.id)}
                  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setSelectedId(p.job.id); } }}>
                  <title>{`${p.job.title}, ${p.job.companyName}: ${p.score}%`}</title>
                  {p.job.id === selectedId && <circle className="sel" r="15" />}
                  {p.job.myApplicationStatus && <circle className="halo" r="11.5" />}
                  <circle className="dot" r={p.job.id === selectedId ? 9 : 7} />
                  {label && <text className="node-label" x={label.right ? 13 : -13} y={label.dy} textAnchor={label.right ? 'start' : 'end'}>{label.text}</text>}
                </g>
              );
            })}
          </svg>
          <div className="map-legend">
            <span><i className="reach" />Within reach</span>
            <span><i />Not yet</span>
            <span><i className="blocked" />CGPA cutoff not met</span>
            <span><i className="applied" />Applied</span>
          </div>
        </div>

        <div className="side">
          <section>
            <h3>Skill levers</h3>
            <p className="muted small">Try on a skill (or a higher level of one you have) and watch which jobs move inside the ring. The number is how many jobs that skill alone brings within reach.</p>
            <div className="levers">
              {leverList.map((skill) => {
                const o = oppBySkill[skill];
                const on = levers.includes(skill);
                return (
                  <button key={skill} className={`lever ${on ? 'on' : ''}`} aria-pressed={on} onClick={() => toggle(skill)}>
                    <span>
                      <b>{o ? actionLabel(o).replace(/^Learn /, '') : simLabel(skill)}</b>
                      <small>{o ? `asked for by ${o.jobsRequiring} open job${o.jobsRequiring === 1 ? '' : 's'}` : 'from Copilot'}</small>
                    </span>
                    <span className="gain">{o ? `+${o.jobsUnlocked}` : ''}</span>
                  </button>
                );
              })}
            </div>
            {sim && (
              <div className="sim-line" aria-live="polite">
                <span className="big">{sim.reachableBefore}</span>
                <span className="muted">now</span>
                <span className="muted" aria-hidden="true">to</span>
                <span className="big after">{sim.reachableAfter}</span>
                <span className="muted">with {levers.map(simLabel).join(' + ')}</span>
              </div>
            )}
            {levers.length > 0 && (
              <button className="btn btn-ghost btn-small" style={{ marginTop: 10 }} onClick={() => setLevers([])}>Reset levers</button>
            )}
          </section>

          <section className="picked">
            {selected ? (
              <>
                <h3>{selected.job.title}</h3>
                <div className="muted small">{selected.job.companyName}, {selected.job.location}</div>
                <div className="card-head" style={{ marginTop: 10 }}>
                  <span className="score num">{selected.score}%</span>
                  {selected.job.myApplicationStatus ? <StatusBadge status={selected.job.myApplicationStatus} /> : <MatchPill score={selected.score} />}
                </div>
                {selected.moved && <p className="small muted">{selected.job.match.score}% today. The levers you picked raise it to {selected.score}%.</p>}
                <MatchBreakdown match={selected.job.match} />
                <Link className="btn btn-ghost btn-small" style={{ marginTop: 12 }} to={`/student/jobs/${selected.job.id}`}>Open job</Link>
              </>
            ) : (
              <p className="muted small">Select any point on the map to see why it sits where it does.</p>
            )}
          </section>
        </div>
      </div>
    </>
  );
}
