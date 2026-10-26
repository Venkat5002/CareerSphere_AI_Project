import { useCallback, useEffect, useRef, useState } from 'react';
import { ErrorBox, Loader, PageHeader, SuccessBox, formatBytes, formatDate } from '../../components/ui';
import SkillInput from '../../components/SkillInput';
import ResumeSkills from '../../components/ResumeSkills';
import { useAuth } from '../../context/AuthContext';
import { errorMessage, fieldErrors } from '../../services/api';
import { resumeService, validateResume } from '../../services/resumeService';
import { studentService } from '../../services/studentService';

const EMPTY = {
  fullName: '', phone: '', college: '', branch: '', graduationYear: '', cgpa: '',
  tenthPercentage: '', twelfthPercentage: '', about: '', linkedinUrl: '', githubUrl: '', skills: [], skillLevels: {},
};

/** Converts API data (numbers/null) to form strings and back. */
const toForm = (p) => Object.fromEntries(Object.keys(EMPTY).map((k) => [k, p[k] ?? EMPTY[k]]));
const num = (v) => (v === '' || v === null ? null : Number(v));

export default function StudentProfile() {
  const { updateName } = useAuth();
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const [saved, setSaved] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    setLoadError('');
    studentService.getProfile()
      .then((p) => { setProfile(p); setForm(toForm(p)); })
      .catch((e) => setLoadError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  // Resume changes only refresh the completion bar, so unsaved edits in the form are kept.
  const refreshProfile = useCallback(() => {
    studentService.getProfile().then(setProfile).catch(() => {});
  }, []);
  // Skills confirmed from the resume are already saved; show them in the form too.
  const onSkillsAdded = useCallback((p) => {
    setProfile(p);
    setForm((f) => ({ ...f, skills: p.skills, skillLevels: { ...p.skillLevels, ...f.skillLevels } }));
  }, []);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function onSave(e) {
    e.preventDefault();
    setError(''); setFields({}); setSaved(''); setSaving(true);
    try {
      const updated = await studentService.updateProfile({
        ...form,
        graduationYear: num(form.graduationYear),
        cgpa: num(form.cgpa),
        tenthPercentage: num(form.tenthPercentage),
        twelfthPercentage: num(form.twelfthPercentage),
      });
      setProfile(updated);
      setForm(toForm(updated));
      updateName(updated.fullName);
      setSaved('Profile saved.');
    } catch (err) {
      setError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setSaving(false);
    }
  }

  if (loadError) return <ErrorBox message={loadError} onRetry={load} />;
  if (!profile) return <Loader />;

  const field = (key, label, props = {}) => (
    <label>
      {label}
      <input value={form[key]} onChange={set(key)} {...props} />
      {fields[key] && <span className="field-error">{fields[key]}</span>}
    </label>
  );

  return (
    <>
      <PageHeader title="Profile" subtitle={`${profile.completion}% complete${profile.missingItems.length ? `. Missing: ${profile.missingItems.join(', ')}` : ''}`} />
      <div className="progress"><span style={{ width: `${profile.completion}%` }} /></div>

      <ResumeCard onChange={refreshProfile} onSkillsAdded={onSkillsAdded} />

      <form className="card form" onSubmit={onSave}>
        <ErrorBox message={error} />
        <SuccessBox message={saved} />

        <h3>Personal</h3>
        <div className="grid-3">
          {field('fullName', 'Full name', { required: true })}
          <label>Email<input value={profile.email} disabled /></label>
          {field('phone', 'Phone', { inputMode: 'tel' })}
        </div>

        <h3>Academic</h3>
        <div className="grid-3">
          {field('college', 'College')}
          {field('branch', 'Branch', { placeholder: 'e.g. CSE' })}
          {field('graduationYear', 'Graduation year', { type: 'number', min: 2000, max: 2100 })}
          {field('cgpa', 'CGPA (out of 10)', { type: 'number', step: '0.01', min: 0, max: 10 })}
          {field('tenthPercentage', '10th %', { type: 'number', step: '0.01', min: 0, max: 100 })}
          {field('twelfthPercentage', '12th %', { type: 'number', step: '0.01', min: 0, max: 100 })}
        </div>

        <h3>Skills</h3>
        <p className="muted small">Matching and Opportunity Math are calculated from these. Add only skills you can talk about in an interview. Click the level on a skill to change it: Beginner (you've used it in coursework), Intermediate (you've built something with it), Advanced (you could teach it). A job that asks for a higher level gives half credit.</p>
        <SkillInput
          value={form.skills}
          onChange={(skills) => setForm((f) => ({ ...f, skills }))}
          levels={form.skillLevels}
          onLevelsChange={(skillLevels) => setForm((f) => ({ ...f, skillLevels }))}
        />
        {fields.skills && <span className="field-error">{fields.skills}</span>}

        <h3>Professional</h3>
        <label>About you
          <textarea rows={3} value={form.about} onChange={set('about')} maxLength={2000} />
        </label>
        <div className="grid-2">
          {field('linkedinUrl', 'LinkedIn URL', { type: 'url', placeholder: 'https://linkedin.com/in/...' })}
          {field('githubUrl', 'GitHub URL', { type: 'url', placeholder: 'https://github.com/...' })}
        </div>

        <div className="form-actions">
          <button className="btn btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Save profile'}</button>
        </div>
      </form>
    </>
  );
}

/** Upload / replace / view / delete the resume. */
export function ResumeCard({ onChange, onSkillsAdded }) {
  const [resume, setResume] = useState(undefined); // undefined = loading, null = none
  const [file, setFile] = useState(null);
  const [progress, setProgress] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [runKey, setRunKey] = useState(0); // bumps after each upload to read the new resume
  const inputRef = useRef(null);

  useEffect(() => {
    resumeService.get().then(setResume).catch((e) => { setResume(null); setError(errorMessage(e)); });
  }, []);

  function onPick(e) {
    const picked = e.target.files?.[0] || null;
    setMessage('');
    const problem = picked ? validateResume(picked) : null;
    setError(problem || '');
    setFile(problem ? null : picked);
  }

  async function onUpload() {
    setError(''); setMessage(''); setBusy(true); setProgress(0);
    try {
      const saved = await resumeService.upload(file, setProgress);
      setResume(saved);
      setFile(null);
      if (inputRef.current) inputRef.current.value = '';
      setMessage(resume ? 'Resume replaced.' : 'Resume uploaded.');
      setRunKey((k) => k + 1);
      onChange();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!window.confirm('Delete your resume? You will need one to apply for jobs.')) return;
    setError(''); setBusy(true);
    try {
      await resumeService.remove();
      setResume(null);
      setMessage('Resume deleted.');
      onChange();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function onView() {
    setError('');
    try { await resumeService.open(); } catch (err) { setError(errorMessage(err)); }
  }

  return (
    <section className="card resume-card">
      <div className="card-head"><h3>Resume</h3><span className="muted small">PDF, DOC or DOCX up to 5 MB</span></div>
      <ErrorBox message={error} />
      <SuccessBox message={message} />

      {resume === undefined ? <Loader label="Checking resume..." /> : resume ? (
        <div className="resume-current">
          <div className="file-icon">{resume.originalFileName.split('.').pop().toUpperCase()}</div>
          <div className="grow">
            <b>{resume.originalFileName}</b>
            <div className="muted small">{formatBytes(resume.fileSize)}, uploaded {formatDate(resume.uploadedAt)}</div>
          </div>
          <button type="button" className="btn btn-ghost btn-small" onClick={onView}>View</button>
          <button type="button" className="btn btn-ghost btn-small danger" onClick={onDelete} disabled={busy}>Delete</button>
        </div>
      ) : (
        <p className="muted">No resume uploaded yet.</p>
      )}

      <div className="resume-upload">
        <input ref={inputRef} type="file" accept=".pdf,.doc,.docx" onChange={onPick} disabled={busy} aria-label="Choose resume file" />
        {file && <span className="small">{file.name} ({formatBytes(file.size)})</span>}
        <button type="button" className="btn btn-primary btn-small" onClick={onUpload} disabled={!file || busy}>
          {busy ? `Uploading ${progress}%` : resume ? 'Replace resume' : 'Upload resume'}
        </button>
      </div>
      {busy && <div className="progress thin"><span style={{ width: `${progress}%` }} /></div>}
      {resume && <ResumeSkills runKey={runKey} onAdded={onSkillsAdded} />}
    </section>
  );
}
