import { useCallback, useEffect, useState } from 'react';
import { ErrorBox, Loader, PageHeader, SuccessBox, formatDate } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { authService } from '../../services/authService';
import { placementService } from '../../services/placementService';

const EMPTY = { name: '', issuer: '', credentialId: '', verificationUrl: '', issueDate: '', validUntil: '', skill: '' };
const STATUS = { PENDING: 'Under review', VERIFIED: 'Verified', REJECTED: 'Rejected' };

/** Certificates from outside the college, verified by the placement cell. */
export default function Certifications() {
  const [list, setList] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [adding, setAdding] = useState(false);

  const load = useCallback(() => {
    setError('');
    placementService.certificates().then(setList).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load]);

  async function onDelete(c) {
    setError(''); setNotice('');
    try { await placementService.deleteCertificate(c.id); setNotice(`Removed ${c.name}.`); load(); }
    catch (e) { setError(errorMessage(e)); }
  }

  async function onView(c) {
    try { await placementService.openCertificate(c.id); } catch (e) { setError(errorMessage(e)); }
  }

  if (error && !list) return <ErrorBox message={error} onRetry={load} />;
  if (!list) return <Loader />;
  const verified = list.filter((c) => c.status === 'VERIFIED').length;
  const pending = list.filter((c) => c.status === 'PENDING').length;

  return (
    <>
      <PageHeader title="Certifications" subtitle="Courses, exams and workshops you completed outside the college."
        actions={<button className="btn btn-primary" onClick={() => setAdding(true)}>Add certificate</button>} />

      <div className="stat-grid">
        <div className="stat"><div className="stat-label">Verified</div><div className="stat-value">{verified}</div><div className="stat-hint">5 résumé points each, up to 2</div></div>
        <div className="stat"><div className="stat-label">Under review</div><div className="stat-value">{pending}</div><div className="stat-hint">The placement cell checks these</div></div>
        <div className="stat"><div className="stat-label">Leaderboard</div><div className="stat-value">+{verified * 10}</div><div className="stat-hint">10 readiness points per verified certificate</div></div>
      </div>

      <section className="card explain">
        <b>How it works.</b> Finish the course on the provider's site (NPTEL, Coursera, AWS, a vendor exam...), then add the
        certificate here with its credential ID or verification link. The placement cell checks it. A verified certificate
        that names a skill adds that skill to your profile as verified at Intermediate.
      </section>

      <ErrorBox message={error} />
      <SuccessBox message={notice} />

      {list.length === 0 ? (
        <div className="empty"><h3>No certificates yet</h3><p className="muted">Add one to have it verified.</p>
          <button className="btn btn-primary" onClick={() => setAdding(true)}>Add certificate</button></div>
      ) : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Certificate</th><th>Issued by</th><th>Skill</th><th>Submitted</th><th>Status</th><th /></tr></thead>
            <tbody>
              {list.map((c) => (
                <tr key={c.id}>
                  <td><b>{c.name}</b>{c.credentialId && <div className="muted small">ID {c.credentialId}</div>}
                    {c.verificationUrl && <div className="small"><a className="link" href={c.verificationUrl} target="_blank" rel="noreferrer">Verification link</a></div>}</td>
                  <td>{c.issuer}</td>
                  <td>{c.skill || '-'}</td>
                  <td className="num">{formatDate(c.submittedAt)}</td>
                  <td><span className={`badge cert-${c.status.toLowerCase()}`}>{STATUS[c.status]}</span>
                    {c.reviewNote && <div className="muted small">{c.reviewNote}</div>}</td>
                  <td className="row-actions">
                    <button className="btn btn-ghost btn-small" onClick={() => onView(c)}>View</button>
                    {c.status !== 'VERIFIED' && <button className="btn btn-ghost btn-small danger" onClick={() => onDelete(c)}>Delete</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && <AddCertificate onClose={() => setAdding(false)} onSaved={(c) => { setAdding(false); setNotice(`${c.name} was sent to the placement cell for verification.`); load(); }} />}
    </>
  );
}

function AddCertificate({ onClose, onSaved }) {
  const [form, setForm] = useState(EMPTY);
  const [file, setFile] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  useEffect(() => { authService.skills().then(setCatalog).catch(() => {}); }, []);
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    if (!file) { setError('Attach the certificate as a PDF, PNG or JPG.'); return; }
    if (file.size > 5 * 1024 * 1024) { setError('The file is larger than 5 MB.'); return; }
    setBusy(true);
    try { onSaved(await placementService.addCertificate(form, file)); }
    catch (err) { setError(errorMessage(err)); setBusy(false); }
  }

  return (
    <div className="modal-scrim" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="modal form" role="dialog" aria-modal="true" aria-labelledby="cert-title" onSubmit={onSubmit}>
        <div className="card-head"><h2 id="cert-title">Add a certificate</h2>
          <button type="button" className="icon-btn" aria-label="Close" onClick={onClose}>×</button></div>
        <ErrorBox message={error} />
        <label>Certificate name *<input value={form.name} onChange={set('name')} required maxLength={150} placeholder="AWS Certified Cloud Practitioner" /></label>
        <label>Issued by *<input value={form.issuer} onChange={set('issuer')} required maxLength={120} placeholder="Amazon Web Services, NPTEL, Coursera..." /></label>
        <div className="grid-2">
          <label>Credential ID<input value={form.credentialId} onChange={set('credentialId')} maxLength={120} /></label>
          <label>Skill it proves
            <input list="cert-skills" value={form.skill} onChange={set('skill')} maxLength={60} placeholder="e.g. AWS" />
            <datalist id="cert-skills">{catalog.map((s) => <option key={s.name} value={s.name} />)}</datalist>
          </label>
        </div>
        <label>Verification link<input type="url" value={form.verificationUrl} onChange={set('verificationUrl')} placeholder="https://..." />
          <span className="muted small">A link where the issuer confirms the certificate is the fastest way to get verified.</span></label>
        <div className="grid-2">
          <label>Issue date<input type="date" value={form.issueDate} onChange={set('issueDate')} /></label>
          <label>Valid until<input type="date" value={form.validUntil} onChange={set('validUntil')} /><span className="muted small">Leave empty if it doesn't expire.</span></label>
        </div>
        <label>Certificate file *<input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={(e) => setFile(e.target.files?.[0] || null)} />
          <span className="muted small">PDF, PNG or JPG, up to 5 MB.</span></label>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Sending...' : 'Submit for verification'}</button>
        </div>
      </form>
    </div>
  );
}
