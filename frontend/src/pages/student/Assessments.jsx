import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorBox, Loader, PageHeader, formatDate } from '../../components/ui';
import { levelName } from '../../components/SkillInput';
import { errorMessage } from '../../services/api';
import { placementService } from '../../services/placementService';

/** Skill assessments: pick a skill, answer 8 questions in 10 minutes, get a verified level. */
export default function Assessments() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(null);
  const [result, setResult] = useState(null);
  const [starting, setStarting] = useState('');

  const load = useCallback(() => {
    setError('');
    placementService.assessments().then(setData).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  async function start(skill) {
    setError(''); setStarting(skill); setResult(null);
    try { setAttempt(await placementService.startAssessment(skill)); }
    catch (e) { setError(errorMessage(e)); }
    finally { setStarting(''); }
  }

  if (attempt) {
    return <TestRunner attempt={attempt} onDone={(r) => { setAttempt(null); setResult(r); load(); }}
      onError={(m) => { setAttempt(null); setError(m); load(); }} />;
  }
  if (error && !data) return <ErrorBox message={error} onRetry={load} />;
  if (!data) return <Loader />;

  const mine = data.skills.filter((s) => s.inProfile);
  const others = data.skills.filter((s) => !s.inProfile);

  return (
    <>
      <PageHeader title="Skill assessments"
        subtitle={`${data.questions} questions, ${data.minutes} minutes, graded on the server. ${data.passRule}`} />
      <ErrorBox message={error} />
      {result && <ResultCard result={result} onClose={() => setResult(null)} />}
      {!data.unlocked && (
        <div className="pp-next warn">
          <div><b>Assessments are locked.</b><p>{data.lockedReason}</p></div>
          <Link className="btn btn-primary" to="/student/placement">Open placement profile</Link>
        </div>
      )}
      <section>
        <h2>Skills in your profile</h2>
        {mine.length === 0 ? <p className="muted">None of your skills has a test yet. Pick one below to verify a new skill.</p> : (
          <div className="assess-grid">{mine.map((s) => <SkillTest key={s.skill} s={s} locked={!data.unlocked} busy={starting === s.skill} onStart={start} />)}</div>
        )}
      </section>
      <section>
        <h2>More tests</h2>
        <p className="muted small">Passing a test for a skill you haven't listed adds it to your profile at the verified level.</p>
        <div className="assess-grid">{others.map((s) => <SkillTest key={s.skill} s={s} locked={!data.unlocked} busy={starting === s.skill} onStart={start} />)}</div>
      </section>
    </>
  );
}

function SkillTest({ s, locked, busy, onStart }) {
  const wait = s.canRetakeAt && new Date(s.canRetakeAt) > new Date();
  return (
    <article className="assess-card">
      <div className="assess-top">
        <b>{s.skill}</b>
        {s.verifiedLevel > 0 ? <span className="chip chip-verified">✓ {s.verifiedLabel}</span>
          : s.inProfile ? <span className="chip-state warn">Not verified</span> : null}
      </div>
      <div className="muted small">
        {s.inProfile ? `Your level: ${levelName(s.yourLevel)}` : 'Not in your profile'}
        {s.lastTotal != null && <> · Last: {s.lastCorrect}/{s.lastTotal} on {formatDate(s.lastTakenAt)}</>}
      </div>
      <button className="btn btn-primary btn-small" disabled={locked || busy || wait} onClick={() => onStart(s.skill)}>
        {busy ? 'Starting...' : wait ? `Try again after ${new Date(s.canRetakeAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
          : s.lastTotal != null ? 'Retake test' : 'Start test'}
      </button>
    </article>
  );
}

function TestRunner({ attempt, onDone, onError }) {
  const [answers, setAnswers] = useState(() => attempt.questions.map(() => null));
  const [current, setCurrent] = useState(0);
  const [left, setLeft] = useState(() => Math.max(0, Math.round((new Date(attempt.endsAt) - Date.now()) / 1000)));
  const [sending, setSending] = useState(false);
  const total = attempt.questions.length;
  const answered = answers.filter((a) => a != null).length;

  const submit = useCallback(async () => {
    if (sending) return;
    setSending(true);
    try { onDone(await placementService.submitAssessment(attempt.attemptId, answers)); }
    catch (e) { onError(errorMessage(e)); }
  }, [answers, attempt.attemptId, onDone, onError, sending]);

  useEffect(() => {
    if (left <= 0) { submit(); return undefined; }
    const t = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [left, submit]);

  const q = attempt.questions[current];
  const mm = String(Math.floor(left / 60)).padStart(1, '0'), ss = String(left % 60).padStart(2, '0');

  return (
    <section className="card test">
      <div className="test-head">
        <div><span className="eyebrow">Skill assessment</span><h2>{attempt.skill}</h2></div>
        <div className={`timer ${left < 60 ? 'low' : ''}`} aria-live="polite">{mm}:{ss}</div>
      </div>
      <div className="test-dots" role="tablist" aria-label="Questions">
        {attempt.questions.map((_, i) => (
          <button key={i} role="tab" aria-selected={i === current} className={`${i === current ? 'here' : ''} ${answers[i] != null ? 'done' : ''}`}
            onClick={() => setCurrent(i)}>{i + 1}</button>
        ))}
      </div>
      <fieldset className="question">
        <legend><span className="muted small">Question {current + 1} of {total}</span><br />{q.question}</legend>
        {q.options.map((o, i) => (
          <label key={i} className={`option ${answers[current] === i ? 'picked' : ''}`}>
            <input type="radio" name={`q${current}`} checked={answers[current] === i}
              onChange={() => setAnswers((a) => a.map((x, j) => (j === current ? i : x)))} />
            {o}
          </label>
        ))}
      </fieldset>
      <div className="test-foot">
        <button className="btn btn-ghost" disabled={current === 0} onClick={() => setCurrent((c) => c - 1)}>Previous</button>
        <span className="muted small grow center">{answered} of {total} answered</span>
        {current < total - 1
          ? <button className="btn btn-ghost" onClick={() => setCurrent((c) => c + 1)}>Next</button>
          : <button className="btn btn-primary" disabled={sending} onClick={submit}>{sending ? 'Submitting...' : 'Submit answers'}</button>}
      </div>
    </section>
  );
}

function ResultCard({ result, onClose }) {
  const passed = result.verifiedLevel > 0;
  return (
    <section className={`pp-next ${passed ? 'good' : 'warn'}`}>
      <div className="grow">
        <b>{result.skill}: {result.correct} of {result.total} correct.</b>
        <p>{result.message}</p>
        <div className="result-dots" aria-label="Right and wrong answers">
          {result.correctByQuestion.map((ok, i) => <span key={i} className={ok ? 'ok' : 'no'} title={`Question ${i + 1}: ${ok ? 'right' : 'wrong'}`}>{ok ? '✓' : '✗'}</span>)}
        </div>
      </div>
      <button className="btn btn-ghost btn-small" onClick={onClose}>Close</button>
    </section>
  );
}
