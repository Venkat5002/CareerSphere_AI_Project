import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ErrorBox, Loader, PageHeader } from '../../components/ui';
import SkillInput from '../../components/SkillInput';
import { errorMessage, fieldErrors } from '../../services/api';
import { recruiterService } from '../../services/recruiterService';

const EMPTY = {
  title: '', companyName: '', location: '', salary: '', jobType: 'Full-time', experience: '',
  description: '', minCgpa: '', deadline: '', openings: '', skills: [], skillLevels: {}, niceToHave: [],
};

/** Create a new job, or edit an existing one (route /recruiter/jobs/:id/edit). */
export default function JobForm() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [loading, setLoading] = useState(editing);
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!editing) return;
    recruiterService.jobs()
      .then((jobs) => {
        const job = jobs.find((j) => String(j.id) === id);
        if (!job) throw new Error('not found');
        const loaded = Object.fromEntries(Object.keys(EMPTY).map((k) => [k, job[k] ?? structuredClone(EMPTY[k])]));
        // The API sends requirements as a list; the form keeps them by skill name.
        for (const r of job.requirements || []) {
          if (r.minLevel > 1) loaded.skillLevels[r.skill] = r.minLevel;
          if (!r.mustHave) loaded.niceToHave.push(r.skill);
        }
        setForm(loaded);
      })
      .catch((e) => setError(e.response ? errorMessage(e) : 'Job not found.'))
      .finally(() => setLoading(false));
  }, [editing, id]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function onSubmit(e) {
    e.preventDefault();
    setError(''); setFields({}); setBusy(true);
    const payload = {
      ...form,
      minCgpa: form.minCgpa === '' ? null : Number(form.minCgpa),
      openings: form.openings === '' ? null : Number(form.openings),
      deadline: form.deadline || null,
    };
    try {
      if (editing) await recruiterService.updateJob(id, payload);
      else await recruiterService.createJob(payload);
      navigate('/recruiter/jobs');
    } catch (err) {
      setError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Loader />;

  const field = (key, label, props = {}) => (
    <label>
      {label}
      <input value={form[key]} onChange={set(key)} {...props} />
      {fields[key] && <span className="field-error">{fields[key]}</span>}
    </label>
  );

  return (
    <>
      <PageHeader title={editing ? 'Edit job' : 'Post a job'} subtitle="Required skills drive the match score students see, so list the ones that really matter." />
      <form className="card form" onSubmit={onSubmit}>
        <ErrorBox message={error} />
        <div className="grid-2">
          {field('title', 'Job title', { required: true, placeholder: 'e.g. Java Backend Developer' })}
          {field('companyName', 'Company', { placeholder: 'Leave empty to use your company profile' })}
        </div>
        <div className="grid-3">
          {field('location', 'Location')}
          {field('salary', 'Salary', { placeholder: 'e.g. 6-8 LPA' })}
          <label>Job type
            <select value={form.jobType} onChange={set('jobType')}>
              <option>Full-time</option><option>Internship</option><option>Contract</option>
            </select>
          </label>
          {field('experience', 'Experience', { placeholder: 'e.g. Freshers' })}
          {field('minCgpa', 'Minimum CGPA', { type: 'number', step: '0.1', min: 0, max: 10 })}
          {field('openings', 'Openings', { type: 'number', min: 1 })}
          {field('deadline', 'Apply by', { type: 'date' })}
        </div>
        <label>Required skills</label>
        <p className="muted small">On each skill, click the level to set the minimum you need, and Must-have to mark it Nice to have. Must-haves count twice as much in the match score; a student below the level gets half credit.</p>
        <SkillInput
          value={form.skills}
          onChange={(skills) => setForm((f) => ({ ...f, skills }))}
          levels={form.skillLevels}
          onLevelsChange={(skillLevels) => setForm((f) => ({ ...f, skillLevels }))}
          levelPrefix="Min. "
          nice={form.niceToHave}
          onNiceChange={(niceToHave) => setForm((f) => ({ ...f, niceToHave }))}
        />
        {fields.skills && <span className="field-error">{fields.skills}</span>}
        <label>Description
          <textarea rows={5} value={form.description} onChange={set('description')} maxLength={4000} />
        </label>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/recruiter/jobs')}>Cancel</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving...' : editing ? 'Save changes' : 'Publish job'}</button>
        </div>
      </form>
    </>
  );
}
