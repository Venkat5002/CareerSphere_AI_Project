import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorBox, Loader, SuccessBox } from '../../components/ui';
import { levelName } from '../../components/SkillInput';
import { errorMessage } from '../../services/api';
import { placementService } from '../../services/placementService';
import { studentService } from '../../services/studentService';
import { ResumeCard } from './StudentProfile';

const STEPS = [
  ['RESUME', 'Résumé'],
  ['CONSENT', 'Placement consent'],
  ['ASSESSMENT', 'Skill assessment'],
  ['VIDEO', 'Introduction video'],
];
const initials = (name) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase();

/** Sends the whole profile back with one field changed (the API replaces the profile). */
function asRequest(p, changes) {
  return {
    fullName: p.fullName, phone: p.phone, college: p.college, branch: p.branch, graduationYear: p.graduationYear,
    cgpa: p.cgpa, tenthPercentage: p.tenthPercentage, twelfthPercentage: p.twelfthPercentage, about: p.about,
    linkedinUrl: p.linkedinUrl, githubUrl: p.githubUrl, skills: p.skills, skillLevels: p.skillLevels,
    headline: p.headline ?? '', ...changes,
  };
}

/**
 * The placement profile: one page that walks the student through
 * résumé -> placement consent -> skill assessment -> introduction video,
 * with the résumé score and what each step is worth.
 */
export default function PlacementProfile() {
  const [profile, setProfile] = useState(null);
  const [ready, setReady] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(() => {
    setError('');
    Promise.all([studentService.getProfile(), placementService.readiness()])
      .then(([p, r]) => { setProfile(p); setReady(r); })
      .catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  const refreshReadiness = useCallback(() => {
    placementService.readiness().then(setReady).catch(() => {});
    studentService.getProfile().then(setProfile).catch(() => {});
  }, []);

  async function save(changes, message) {
    setError(''); setNotice('');
    try {
      const p = await studentService.updateProfile(asRequest(profile, changes));
      setProfile(p);
      setNotice(message);
      refreshReadiness();
    } catch (e) { setError(errorMessage(e)); }
  }

  async function toggleConsent() {
    setError(''); setNotice('');
    try {
      const r = await placementService.setConsent(!ready.consent);
      setReady(r);
      setProfile((p) => ({ ...p, placementConsent: r.consent }));
      setNotice(r.consent ? 'Placement consent recorded. You can now apply to jobs and take skill assessments.'
        : 'Consent withdrawn. Recruiters can no longer see your profile, and you can’t apply until you turn it on again.');
    } catch (e) { setError(errorMessage(e)); }
  }

  if (error && !profile) return <ErrorBox message={error} onRetry={load} />;
  if (!profile || !ready) return <Loader label="Loading your placement profile..." />;

  const stepIndex = STEPS.findIndex(([k]) => k === ready.nextStep);
  const verified = Object.entries(profile.verifiedLevels || {});

  return (
    <>
      <section className="pp-head card">
        <div className="pp-banner" aria-hidden="true" />
        <div className="pp-id">
          <span className="pp-avatar">{initials(profile.fullName)}</span>
          <div className="grow">
            <h1>{profile.fullName}</h1>
            <Headline value={profile.headline} onSave={(headline) => save({ headline }, 'Headline saved.')} />
            <div className="pp-facts muted small">
              {[profile.branch, profile.college, profile.graduationYear && `Class of ${profile.graduationYear}`,
                profile.cgpa != null && `CGPA ${profile.cgpa}`, profile.email, profile.phone].filter(Boolean).join(' · ')}
            </div>
            <div className="pp-chips">
              <span className={`chip-state ${ready.hasResume ? 'on' : ''}`}>{ready.hasResume ? 'Résumé on file' : 'No résumé'}</span>
              <span className={`chip-state ${ready.consent ? 'on' : 'warn'}`}>{ready.consent ? 'Consent given' : 'No consent'}</span>
              {verified.length > 0 && <span className="chip-state on">{verified.length} verified skill{verified.length === 1 ? '' : 's'}</span>}
            </div>
          </div>
          <div className="pp-switch">
            <div>
              <b>I consent to share my profile for placements</b>
              <div className="muted small">{ready.consent ? 'On: recruiters and the placement cell can see your profile.' : 'Turn on to unlock applications and assessments.'}</div>
            </div>
            <button type="button" role="switch" aria-checked={ready.consent} className={`switch ${ready.consent ? 'on' : ''}`}
              onClick={toggleConsent} disabled={!ready.hasResume && !ready.consent}
              title={!ready.hasResume ? 'Upload your résumé first' : undefined}>
              <span />
            </button>
          </div>
        </div>
      </section>

      <ErrorBox message={error} />
      <SuccessBox message={notice} />

      <ol className="steps" aria-label="Placement steps">
        {STEPS.map(([key, label], i) => {
          const state = ready.nextStep === 'DONE' || i < stepIndex ? 'done' : i === stepIndex ? 'current' : 'todo';
          return <li key={key} className={state}><span>{state === 'done' ? '✓' : i + 1}</span>{label}</li>;
        })}
      </ol>

      <NextStep ready={ready} onConsent={toggleConsent} />

      <div className="pp-grid">
        <div className="pp-main">
          <VideoCard hasVideo={ready.hasVideo} onChange={refreshReadiness} />
          <ResumeCard onChange={refreshReadiness} onSkillsAdded={() => refreshReadiness()} />
          <AssessmentCard ready={ready} verified={verified} />
          <AboutCard about={profile.about} onSave={(about) => save({ about }, 'About saved.')} />
        </div>
        <ScoreCard ready={ready} />
      </div>
    </>
  );
}

function Headline({ value, onSave }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(value || '');
  if (!editing) {
    return (
      <div className="pp-headline">
        {value ? <span>{value}</span> : <span className="muted">Add the role you're aiming for</span>}
        <button type="button" className="btn btn-ghost btn-small" onClick={() => { setText(value || ''); setEditing(true); }}>Edit</button>
      </div>
    );
  }
  return (
    <form className="pp-headline" onSubmit={(e) => { e.preventDefault(); onSave(text.trim()); setEditing(false); }}>
      <input value={text} onChange={(e) => setText(e.target.value)} maxLength={80} placeholder="e.g. Java Backend Developer" aria-label="Headline" autoFocus />
      <button className="btn btn-primary btn-small">Save</button>
      <button type="button" className="btn btn-ghost btn-small" onClick={() => setEditing(false)}>Cancel</button>
    </form>
  );
}

function NextStep({ ready, onConsent }) {
  switch (ready.nextStep) {
    case 'RESUME':
      return <div className="pp-next warn"><div><b>Upload your résumé to start.</b><p>Your résumé is what recruiters open first, and it unlocks placement consent.</p></div></div>;
    case 'CONSENT':
      return (
        <div className="pp-next warn">
          <div><b>Give placement consent to continue.</b><p>Your résumé is uploaded. Consent lets recruiters see your profile, and unlocks applying and the skill assessment.</p></div>
          <button className="btn btn-primary" onClick={onConsent}>Give consent</button>
        </div>
      );
    case 'ASSESSMENT':
      return (
        <div className="pp-next">
          <div><b>Next step: take a skill assessment.</b><p>10 minutes, 8 questions. Passing verifies your level, and recruiters see a verified mark.</p></div>
          <Link className="btn btn-primary" to="/student/assessments">Take skill assessment</Link>
        </div>
      );
    case 'VIDEO':
      return <div className="pp-next"><div><b>Last step: record a one-minute introduction.</b><p>Recruiters use it to hear how you communicate. Use the Introduction video card below.</p></div></div>;
    default:
      return <div className="pp-next good"><div><b>Your placement profile is complete.</b><p>Keep verifying skills and adding certificates to climb the leaderboard.</p></div><Link className="btn btn-ghost" to="/student/leaderboard">See leaderboard</Link></div>;
  }
}

/** Résumé score ring and what each point is for. */
function ScoreCard({ ready }) {
  const r = 34, c = 2 * Math.PI * r, pct = ready.resumeScore / 100;
  return (
    <aside className="card pp-score">
      <div className="card-head"><h3>Résumé score</h3><span className="muted small">{ready.points} points</span></div>
      <div className="pp-ring">
        <svg viewBox="0 0 80 80" width="96" height="96" role="img" aria-label={`Résumé score ${ready.resumeScore} out of 100`}>
          <circle cx="40" cy="40" r={r} fill="none" stroke="var(--paper-2)" strokeWidth="8" />
          <circle cx="40" cy="40" r={r} fill="none" stroke="var(--ink)" strokeWidth="8" strokeLinecap="round"
            strokeDasharray={`${c * pct} ${c}`} transform="rotate(-90 40 40)" />
          <text x="40" y="46" textAnchor="middle" fontSize="18" fontWeight="700" fill="var(--ink)">{ready.resumeScore}%</text>
        </svg>
        <p className="muted small">Calculated from your profile with fixed rules, so the same profile always gets the same score.
          {ready.rankInBatch && <> You're <b>#{ready.rankInBatch}</b> of {ready.batchSize} in the batch.</>}</p>
      </div>
      <ul className="score-list">
        {ready.breakdown.map((i) => (
          <li key={i.label} className={i.points >= i.max ? 'full' : ''}>
            <div className="score-row"><span>{i.label}</span><b className="num">{i.points}/{i.max}</b></div>
            {i.tip && <div className="muted small">{i.tip}</div>}
          </li>
        ))}
      </ul>
    </aside>
  );
}

function AssessmentCard({ ready, verified }) {
  const locked = !ready.hasResume || !ready.consent;
  return (
    <section className={`card pp-card ${locked ? 'locked' : ''}`}>
      <div className="card-head">
        <h3>{locked ? 'Skill assessment: locked' : 'Skill assessment'}</h3>
        {!locked && <Link className="btn btn-primary btn-small" to="/student/assessments">Take an assessment</Link>}
      </div>
      {locked ? (
        <p className="muted">{!ready.hasResume ? 'Upload your résumé, then give placement consent, to unlock it.' : 'Give placement consent to unlock it.'}</p>
      ) : verified.length ? (
        <div className="skill-row">
          {verified.map(([skill, level]) => <span key={skill} className="chip chip-verified">✓ {skill} · {levelName(level)}</span>)}
        </div>
      ) : (
        <p className="muted">No skills verified yet. Each test is 8 questions in 10 minutes; passing sets your level and marks it verified.</p>
      )}
    </section>
  );
}

function AboutCard({ about, onSave }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(about || '');
  return (
    <section className="card pp-card">
      <div className="card-head">
        <h3>About</h3>
        {!editing && <button className="btn btn-ghost btn-small" onClick={() => { setText(about || ''); setEditing(true); }}>Edit</button>}
      </div>
      {editing ? (
        <form className="form" onSubmit={(e) => { e.preventDefault(); onSave(text.trim()); setEditing(false); }}>
          <textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} aria-label="About you" />
          <div className="form-actions">
            <span className="muted small grow">{text.trim().length} characters; 150 or more earns full points.</span>
            <button type="button" className="btn btn-ghost btn-small" onClick={() => setEditing(false)}>Cancel</button>
            <button className="btn btn-primary btn-small">Save</button>
          </div>
        </form>
      ) : about ? <p className="pre">{about}</p> : <p className="muted">Two or three sentences on what you build and the role you want.</p>}
    </section>
  );
}

const MAX_SECONDS = 60;

/** Record (camera) or upload a one-minute introduction video, then play or replace it. */
function VideoCard({ hasVideo, onChange }) {
  const [url, setUrl] = useState(null);
  const [mode, setMode] = useState('idle');          // idle | recording | review | uploading
  const [seconds, setSeconds] = useState(0);
  const [recorded, setRecorded] = useState(null);    // { blob, url, ext }
  const [error, setError] = useState('');
  const liveRef = useRef(null);
  const streamRef = useRef(null);
  const recorderRef = useRef(null);
  const fileRef = useRef(null);
  const canRecord = typeof window !== 'undefined' && window.isSecureContext && navigator.mediaDevices?.getUserMedia && typeof MediaRecorder !== 'undefined';

  useEffect(() => {
    let cancelled = false, made = null;
    if (hasVideo) placementService.videoUrl().then((u) => { if (cancelled) URL.revokeObjectURL(u); else { made = u; setUrl(u); } }).catch(() => {});
    else setUrl(null);
    return () => { cancelled = true; if (made) URL.revokeObjectURL(made); };
  }, [hasVideo]);

  useEffect(() => () => streamRef.current?.getTracks().forEach((t) => t.stop()), []);

  useEffect(() => {
    if (mode !== 'recording') return undefined;
    const t = setInterval(() => setSeconds((s) => {
      if (s + 1 >= MAX_SECONDS) recorderRef.current?.state === 'recording' && recorderRef.current.stop();
      return s + 1;
    }), 1000);
    return () => clearInterval(t);
  }, [mode]);

  async function startRecording() {
    setError('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 }, audio: true });
      streamRef.current = stream;
      if (liveRef.current) { liveRef.current.srcObject = stream; liveRef.current.play().catch(() => {}); }
      const type = ['video/webm;codecs=vp9,opus', 'video/webm', 'video/mp4'].find((t) => MediaRecorder.isTypeSupported?.(t)) || '';
      const rec = new MediaRecorder(stream, type ? { mimeType: type, videoBitsPerSecond: 1_500_000 } : undefined);
      const chunks = [];
      rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      rec.onstop = () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunks, { type: rec.mimeType || 'video/webm' });
        const ext = (rec.mimeType || '').includes('mp4') ? 'mp4' : 'webm';
        setRecorded({ blob, url: URL.createObjectURL(blob), ext });
        setMode('review');
      };
      recorderRef.current = rec;
      setSeconds(0);
      rec.start(1000);
      setMode('recording');
    } catch (e) {
      setError(e?.name === 'NotAllowedError' ? 'Camera access was blocked. Allow the camera, or upload a video file instead.'
        : 'Could not start the camera. You can upload a video file instead.');
    }
  }

  async function upload(blob, name) {
    setError(''); setMode('uploading');
    try {
      await placementService.uploadVideo(blob, name);
      if (recorded) URL.revokeObjectURL(recorded.url);
      setRecorded(null);
      setMode('idle');
      onChange();
    } catch (e) {
      setError(errorMessage(e)); setMode(recorded ? 'review' : 'idle');
    }
  }

  function onFile(e) {
    const f = e.target.files?.[0];
    if (!f) return;
    if (f.size > 25 * 1024 * 1024) { setError('The video is larger than 25 MB. Record a shorter one or lower the quality.'); return; }
    upload(f, f.name);
  }

  async function remove() {
    setError('');
    try { await placementService.deleteVideo(); onChange(); } catch (e) { setError(errorMessage(e)); }
  }

  return (
    <section className="card pp-card">
      <div className="card-head">
        <h3>Introduction video</h3>
        <span className={`chip-state ${hasVideo ? 'on' : 'warn'}`}>{hasVideo ? 'Recorded' : 'Not recorded'}</span>
      </div>
      <p className="muted small">A short self-introduction, at most one minute. Recruiters who see your application can watch it.</p>
      <ErrorBox message={error} />

      {mode === 'recording' && (
        <div className="video-box">
          <video ref={liveRef} muted playsInline aria-label="Camera preview" />
          <div className="rec-bar"><span className="rec-dot" /> Recording {seconds}s of {MAX_SECONDS}s
            <button className="btn btn-primary btn-small" onClick={() => recorderRef.current?.stop()}>Stop</button></div>
        </div>
      )}
      {mode === 'review' && recorded && (
        <div className="video-box">
          <video src={recorded.url} controls playsInline />
          <div className="rec-bar">
            <button className="btn btn-primary btn-small" onClick={() => upload(recorded.blob, `introduction.${recorded.ext}`)}>Save video</button>
            <button className="btn btn-ghost btn-small" onClick={() => { URL.revokeObjectURL(recorded.url); setRecorded(null); startRecording(); }}>Record again</button>
          </div>
        </div>
      )}
      {mode === 'uploading' && <Loader label="Uploading video..." />}
      {mode === 'idle' && hasVideo && url && <div className="video-box"><video src={url} controls playsInline /></div>}

      {(mode === 'idle') && (
        <div className="toolbar">
          {canRecord && <button className="btn btn-primary btn-small" onClick={startRecording}>{hasVideo ? 'Record again' : 'Record introduction'}</button>}
          <button className="btn btn-ghost btn-small" onClick={() => fileRef.current?.click()}>Upload a video file</button>
          {hasVideo && <button className="btn btn-ghost btn-small danger" onClick={remove}>Delete</button>}
          <input ref={fileRef} type="file" accept="video/webm,video/mp4,video/quicktime" capture="user" hidden onChange={onFile} />
        </div>
      )}
      {!canRecord && mode === 'idle' && <p className="muted small">Recording in the page needs a secure connection (https or localhost); you can still upload a video from your phone or computer.</p>}
    </section>
  );
}
