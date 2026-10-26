import { useCallback, useEffect, useState } from 'react';
import { ErrorBox, Loader, PageHeader, SuccessBox } from '../../components/ui';
import { useAuth } from '../../context/AuthContext';
import { errorMessage, fieldErrors } from '../../services/api';
import { recruiterService } from '../../services/recruiterService';

const KEYS = ['fullName', 'companyName', 'designation', 'website', 'location', 'description'];

export default function RecruiterProfile() {
  const { updateName } = useAuth();
  const [form, setForm] = useState(null);
  const [email, setEmail] = useState('');
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  const [saved, setSaved] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoadError('');
    recruiterService.getProfile()
      .then((p) => { setEmail(p.email); setForm(Object.fromEntries(KEYS.map((k) => [k, p[k] ?? '']))); })
      .catch((e) => setLoadError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function onSubmit(e) {
    e.preventDefault();
    setError(''); setFields({}); setSaved(''); setBusy(true);
    try {
      const p = await recruiterService.updateProfile(form);
      setForm(Object.fromEntries(KEYS.map((k) => [k, p[k] ?? ''])));
      updateName(p.fullName);
      setSaved('Company profile saved.');
    } catch (err) {
      setError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setBusy(false);
    }
  }

  if (loadError) return <ErrorBox message={loadError} onRetry={load} />;
  if (!form) return <Loader />;

  const field = (key, label, props = {}) => (
    <label>
      {label}
      <input value={form[key]} onChange={set(key)} {...props} />
      {fields[key] && <span className="field-error">{fields[key]}</span>}
    </label>
  );

  return (
    <>
      <PageHeader title="Company Profile" subtitle="Your company name is used as the default on new job posts." />
      <form className="card form" onSubmit={onSubmit}>
        <ErrorBox message={error} />
        <SuccessBox message={saved} />
        <div className="grid-2">
          {field('fullName', 'Your name', { required: true })}
          <label>Email<input value={email} disabled /></label>
          {field('companyName', 'Company name')}
          {field('designation', 'Your designation')}
          {field('website', 'Website', { type: 'url', placeholder: 'https://' })}
          {field('location', 'Location')}
        </div>
        <label>About the company
          <textarea rows={4} value={form.description} onChange={set('description')} maxLength={2000} />
        </label>
        <div className="form-actions">
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving...' : 'Save'}</button>
        </div>
      </form>
    </>
  );
}
