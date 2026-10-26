import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  APPLICATION_STATUSES, EmptyState, ErrorBox, Loader, MatchBreakdown, MatchPill, PageHeader, StatusBadge, formatDate, statusLabel,
} from '../../components/ui';
import { errorMessage } from '../../services/api';
import { recruiterService } from '../../services/recruiterService';
import { levelName } from '../../components/SkillInput';

export default function Applicants() {
  const { id } = useParams();
  const [job, setJob] = useState(null);
  const [applicants, setApplicants] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    Promise.all([recruiterService.jobs(), recruiterService.applicants(id)])
      .then(([jobs, list]) => {
        setJob(jobs.find((j) => String(j.id) === id) || null);
        setApplicants(list);
      })
      .catch((e) => setError(errorMessage(e)));
  }, [id]);
  useEffect(load, [load]);

  if (error && !applicants) return <ErrorBox message={error} onRetry={load} />;
  if (!applicants) return <Loader />;

  return (
    <>
      <Link to="/recruiter/jobs" className="link small">Back to my jobs</Link>
      <PageHeader
        title={job ? `Applicants for ${job.title}` : 'Applicants'}
        subtitle="Ranked by skill match. Every score shows which required skills the candidate has."
      />
      <HiddenTalent jobId={id} />
      <h2>Applicants</h2>
      {applicants.length === 0 && <EmptyState title="No applicants yet" />}
      <div className="job-list">
        {applicants.map((a) => (
          <ApplicantCard key={a.applicationId} applicant={a}
            onUpdated={(u) => setApplicants((list) => list.map((x) => (x.applicationId === u.applicationId ? u : x)))} />
        ))}
      </div>
    </>
  );
}

function ApplicantCard({ applicant: a, onUpdated }) {
  const [status, setStatus] = useState(a.status);
  const [note, setNote] = useState(a.recruiterNote || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [videoUrl, setVideoUrl] = useState(null);
  const [saved, setSaved] = useState(false);
  const dirty = status !== a.status || note !== (a.recruiterNote || '');

  async function save() {
    setError(''); setSaved(false); setBusy(true);
    try {
      const updated = await recruiterService.updateStatus(a.applicationId, status, note);
      onUpdated(updated);
      setSaved(true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function watchVideo() {
    setError('');
    try { setVideoUrl(await recruiterService.videoUrl(a.applicationId)); } catch (e) { setError(errorMessage(e)); }
  }

  async function viewResume() {
    setError('');
    try { await recruiterService.openResume(a.applicationId); } catch (e) { setError(errorMessage(e)); }
  }

  return (
    <div className="card applicant">
      <div className="job-card-head">
        <div>
          <h3>{a.studentName}</h3>
          {a.headline && <div className="small">{a.headline}</div>}
          <div className="muted small">
            {[a.branch, a.college, a.cgpa != null && `CGPA ${a.cgpa}`, a.graduationYear && `class of ${a.graduationYear}`].filter(Boolean).join(', ')}
          </div>
          <div className="muted small">{a.email}, applied {formatDate(a.appliedAt)}</div>
        </div>
        <div className="job-card-right">
          <MatchPill score={a.match.score} />
          <StatusBadge status={a.status} />
        </div>
      </div>

      <MatchBreakdown match={a.match} />
      {(Object.keys(a.verifiedLevels || {}).length > 0 || a.verifiedCertificates?.length > 0) && (
        <div className="skill-row" aria-label="Verified by CareerSphere">
          {Object.entries(a.verifiedLevels || {}).map(([skill, lvl]) => (
            <span key={skill} className="chip chip-verified" title="Proved by a skill assessment or a certificate the placement cell verified">✓ {skill} · {levelName(lvl)}</span>
          ))}
          {(a.verifiedCertificates || []).map((c) => <span key={c} className="chip chip-cert">Certificate: {c}</span>)}
        </div>
      )}
      {videoUrl && <div className="video-box"><video src={videoUrl} controls autoPlay playsInline /></div>}

      <div className="applicant-links">
        <button className="btn btn-ghost btn-small" onClick={viewResume} disabled={!a.hasResume}>
          {a.hasResume ? 'View resume' : 'No resume'}
        </button>
        {a.hasVideo && !videoUrl && <button className="btn btn-ghost btn-small" onClick={watchVideo}>Watch introduction</button>}
        {a.githubUrl && <a className="link small" href={a.githubUrl} target="_blank" rel="noreferrer">GitHub</a>}
        {a.linkedinUrl && <a className="link small" href={a.linkedinUrl} target="_blank" rel="noreferrer">LinkedIn</a>}
      </div>

      <ErrorBox message={error} />
      <div className="status-editor">
        <select value={status} onChange={(e) => { setStatus(e.target.value); setSaved(false); }} aria-label="Application status">
          {APPLICATION_STATUSES.map((s) => (
            <option key={s} value={s} disabled={s === 'APPLIED' && a.status !== 'APPLIED'}>{statusLabel(s)}</option>
          ))}
        </select>
        <input value={note} onChange={(e) => { setNote(e.target.value); setSaved(false); }} maxLength={1000}
          placeholder={status === 'REJECTED' ? 'Reason (helps the student improve)' : 'Note for the student (optional)'} aria-label="Note for student" />
        <button className="btn btn-primary btn-small" onClick={save} disabled={!dirty || busy || status === 'APPLIED'}>
          {busy ? 'Saving...' : 'Update'}
        </button>
        {saved && !dirty && <span className="small ok">Saved ✓</span>}
      </div>
    </div>
  );
}

/** Hidden Talent: consented students who haven't applied but fit, or are one learning step away. */
function HiddenTalent({ jobId }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(true);
  const [sent, setSent] = useState({});

  useEffect(() => {
    recruiterService.talent(jobId).then(setData).catch((e) => setError(errorMessage(e)));
  }, [jobId]);

  async function invite(c) {
    setError('');
    try {
      await recruiterService.invite(jobId, c.studentId, `Your profile fits this role${c.stepNeeded ? ` once you ${c.stepNeeded.toLowerCase()}` : ''}. We'd like you to apply.`);
      setSent((s) => ({ ...s, [c.studentId]: true }));
    } catch (e) { setError(errorMessage(e)); }
  }

  if (!data) return error ? <ErrorBox message={error} /> : null;
  const row = (c) => (
    <tr key={c.studentId}>
      <td><b>{c.name}</b>{c.headline && <div className="muted small">{c.headline}</div>}</td>
      <td>{c.branch || '-'}</td>
      <td className="num">{c.cgpa ?? '-'}</td>
      <td><MatchPill score={c.score} /></td>
      <td>{c.stepNeeded || 'Within reach now'}{c.verified.length > 0 && <div className="muted small">✓ {c.verified.join(', ')}</div>}</td>
      <td className="row-actions">{c.invited || sent[c.studentId]
        ? <span className="chip-state on">Invited</span>
        : <button className="btn btn-primary btn-small" onClick={() => invite(c)}>Invite to apply</button>}</td>
    </tr>
  );
  const total = data.withinReach.length + data.oneStepAway.length;
  return (
    <section className="card talent">
      <div className="card-head">
        <div>
          <h2>Hidden talent <span className="first-tag">{total}</span></h2>
          <p className="muted small">Students who haven't applied but fit this job, or are one learning step away. {data.note}</p>
        </div>
        <button className="btn btn-ghost btn-small" onClick={() => setOpen((o) => !o)}>{open ? 'Hide' : 'Show'}</button>
      </div>
      <ErrorBox message={error} />
      {open && (total === 0 ? <p className="muted">Nobody new fits this job yet.</p> : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Student</th><th>Branch</th><th className="num">CGPA</th><th>Match</th><th>What they need</th><th /></tr></thead>
            <tbody>
              {data.withinReach.map(row)}
              {data.oneStepAway.length > 0 && <tr className="group-row"><td colSpan={6}>One step away</td></tr>}
              {data.oneStepAway.map(row)}
            </tbody>
          </table>
        </div>
      ))}
    </section>
  );
}
