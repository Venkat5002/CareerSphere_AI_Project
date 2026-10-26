import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorBox } from './ui';
import { errorMessage } from '../services/api';
import { resumeService } from '../services/resumeService';

/**
 * Shows skills found in the uploaded resume, each with the line it was found in.
 * The student ticks which ones to add; nothing is added without that click.
 * runKey changes after every upload, which starts a new analysis.
 */
export default function ResumeSkills({ runKey, onAdded }) {
  const [state, setState] = useState('idle'); // idle | reading | ready | adding | added
  const [result, setResult] = useState(null);
  const [picked, setPicked] = useState([]);
  const [error, setError] = useState('');
  const [addedCount, setAddedCount] = useState(0);

  async function analyze() {
    setState('reading'); setError(''); setResult(null);
    try {
      const r = await resumeService.findSkills();
      setResult(r);
      setPicked(r.suggestions.filter((s) => !s.alreadyInProfile).map((s) => s.skill));
      setState('ready');
    } catch (e) {
      setError(errorMessage(e));
      setState('idle');
    }
  }

  useEffect(() => { if (runKey) analyze(); }, [runKey]); // eslint-disable-line react-hooks/exhaustive-deps

  async function add() {
    setState('adding'); setError('');
    try {
      const profile = await resumeService.addSkills(picked);
      setAddedCount(picked.length);
      setState('added');
      onAdded(profile);
    } catch (e) {
      setError(errorMessage(e));
      setState('ready');
    }
  }

  const fresh = result?.suggestions.filter((s) => !s.alreadyInProfile) || [];
  const known = result?.suggestions.filter((s) => s.alreadyInProfile) || [];
  const toggle = (skill) => setPicked((p) => (p.includes(skill) ? p.filter((s) => s !== skill) : [...p, skill]));

  return (
    <div className="resume-skills">
      <div className="card-head">
        <h3>Skills in your resume</h3>
        {state !== 'reading' && (
          <button type="button" className="btn btn-ghost btn-small" onClick={analyze}>
            {result ? 'Read again' : 'Find skills in my resume'}
          </button>
        )}
      </div>
      <ErrorBox message={error} />

      {state === 'idle' && !result && (
        <p className="muted small">CareerSphere can read your resume and suggest skills your profile is missing. You choose what to add.</p>
      )}
      {state === 'reading' && <p className="muted small reading">Reading your resume...</p>}

      {result && state !== 'reading' && (
        <>
          <p className="muted small">
            {result.mode === 'gemini'
              ? 'Found by Gemini. Every quote below was checked against your resume text.'
              : 'Found by keyword matching (offline mode).'}
          </p>
          {result.notice && <p className="small notice-line">{result.notice}</p>}

          {state === 'added' ? (
            <div className="alert alert-success">
              <span>Added {addedCount} skill{addedCount === 1 ? '' : 's'} to your profile. Your matches have been recalculated.</span>
              <Link className="link" to="/student/dashboard">See your map</Link>
            </div>
          ) : fresh.length === 0 ? (
            <p className="small">No new skills found. Everything in your resume is already in your profile.</p>
          ) : (
            <>
              <ul className="found-list">
                {fresh.map((s) => (
                  <li key={s.skill}>
                    <label className="found">
                      <input type="checkbox" checked={picked.includes(s.skill)} onChange={() => toggle(s.skill)} />
                      <span className="grow">
                        <b>{s.skill}</b> <span className="muted small">{s.category}</span>
                        <q className="evidence">{s.evidence}</q>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
              <div className="form-actions">
                <button type="button" className="btn btn-primary btn-small" onClick={add} disabled={!picked.length || state === 'adding'}>
                  {state === 'adding' ? 'Adding...' : `Add ${picked.length} skill${picked.length === 1 ? '' : 's'} to my profile`}
                </button>
              </div>
            </>
          )}
          {known.length > 0 && (
            <p className="muted small">Already in your profile: {known.map((s) => s.skill).join(', ')}.</p>
          )}
        </>
      )}
    </div>
  );
}
