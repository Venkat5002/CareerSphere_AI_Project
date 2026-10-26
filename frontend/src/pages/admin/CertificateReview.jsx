import { useCallback, useEffect, useState } from 'react';
import { EmptyState, ErrorBox, Loader, PageHeader, SuccessBox, formatDate } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { adminService } from '../../services/adminService';

const TABS = [['PENDING', 'To review'], ['VERIFIED', 'Verified'], ['REJECTED', 'Rejected'], ['ALL', 'All']];
const STATUS = { PENDING: 'Under review', VERIFIED: 'Verified', REJECTED: 'Rejected' };

/** The placement cell checks students' outside certificates. */
export default function CertificateReview() {
  const [tab, setTab] = useState('PENDING');
  const [list, setList] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [rejecting, setRejecting] = useState(null);
  const [note, setNote] = useState('');

  const load = useCallback(() => {
    setError(''); setList(null);
    adminService.certificates(tab).then(setList).catch((e) => setError(errorMessage(e)));
  }, [tab]);
  useEffect(load, [load]);

  async function review(c, status) {
    setError(''); setNotice('');
    try {
      await adminService.reviewCertificate(c.id, status, status === 'REJECTED' ? note : null);
      setNotice(status === 'VERIFIED'
        ? `Verified ${c.name} for ${c.studentName}.${c.skill ? ` ${c.skill} is now a verified skill on their profile.` : ''}`
        : `Rejected ${c.name}. ${c.studentName} will see your note.`);
      setRejecting(null); setNote('');
      load();
    } catch (e) { setError(errorMessage(e)); }
  }

  return (
    <>
      <PageHeader title="Verify certificates" subtitle="Open each certificate, check the credential ID or link with the issuer, then verify or reject it." />
      <div className="seg" role="tablist">
        {TABS.map(([k, label]) => <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>{label}</button>)}
      </div>
      <ErrorBox message={error} />
      <SuccessBox message={notice} />
      {!list ? <Loader /> : list.length === 0 ? (
        <EmptyState title={tab === 'PENDING' ? 'Nothing waiting for review' : 'No certificates here'}>New certificates appear here as students add them.</EmptyState>
      ) : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Student</th><th>Certificate</th><th>Skill</th><th>Submitted</th><th>Status</th><th /></tr></thead>
            <tbody>
              {list.map((c) => (
                <tr key={c.id}>
                  <td><b>{c.studentName}</b><div className="muted small">{c.studentBranch}</div></td>
                  <td><b>{c.name}</b><div className="muted small">{c.issuer}{c.credentialId && ` · ID ${c.credentialId}`}</div>
                    {c.verificationUrl && <div className="small"><a className="link" href={c.verificationUrl} target="_blank" rel="noreferrer">Check with issuer</a></div>}</td>
                  <td>{c.skill || '-'}</td>
                  <td className="num">{formatDate(c.submittedAt)}</td>
                  <td><span className={`badge cert-${c.status.toLowerCase()}`}>{STATUS[c.status]}</span>
                    {c.reviewNote && <div className="muted small">{c.reviewNote}</div>}</td>
                  <td className="row-actions">
                    <button className="btn btn-ghost btn-small" onClick={() => adminService.openCertificate(c.id).catch((e) => setError(errorMessage(e)))}>View file</button>
                    {c.status === 'PENDING' && rejecting !== c.id && <>
                      <button className="btn btn-primary btn-small" onClick={() => review(c, 'VERIFIED')}>Verify</button>
                      <button className="btn btn-ghost btn-small danger" onClick={() => { setRejecting(c.id); setNote(''); }}>Reject</button>
                    </>}
                    {rejecting === c.id && (
                      <form className="reject-form" onSubmit={(e) => { e.preventDefault(); review(c, 'REJECTED'); }}>
                        <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Why? The student sees this." aria-label="Reason" autoFocus required maxLength={500} />
                        <button className="btn btn-primary btn-small">Reject</button>
                        <button type="button" className="btn btn-ghost btn-small" onClick={() => setRejecting(null)}>Cancel</button>
                      </form>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
