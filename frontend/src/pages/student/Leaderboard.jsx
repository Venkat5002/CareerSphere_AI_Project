import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorBox, Loader, PageHeader } from '../../components/ui';
import { errorMessage } from '../../services/api';
import { adminService } from '../../services/adminService';
import { placementService } from '../../services/placementService';

/** Readiness leaderboard. Students see the top 10 and themselves; the placement cell sees everyone. */
export default function Leaderboard({ admin = false }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    (admin ? adminService.leaderboard() : placementService.leaderboard()).then(setData).catch((e) => setError(errorMessage(e)));
  }, [admin]);
  useEffect(load, [load]);

  if (error) return <ErrorBox message={error} onRetry={load} />;
  if (!data) return <Loader />;
  const you = data.you;
  const max = Math.max(1, ...data.top.map((e) => e.points));
  const showYouBelow = you && !data.top.some((e) => e.you);

  return (
    <>
      <PageHeader title={admin ? 'Batch leaderboard' : 'Leaderboard'}
        subtitle={admin ? 'Every student ranked by placement readiness. Use it to spot who needs help, not to judge.' : 'Placement readiness across your batch. Points come only from things you can improve.'} />

      {you && !admin && (
        <div className="stat-grid">
          <div className="stat"><div className="stat-label">Your rank in the batch</div><div className="stat-value">#{you.rank}</div><div className="stat-hint">of {data.batchSize} students</div></div>
          <div className="stat"><div className="stat-label">Your rank in {you.branch || 'your branch'}</div><div className="stat-value">#{data.rankInBranch ?? '-'}</div><div className="stat-hint">of {data.branchSize}</div></div>
          <div className="stat"><div className="stat-label">Readiness points</div><div className="stat-value">{you.points}</div><div className="stat-hint">Résumé score {you.resumeScore}%</div></div>
        </div>
      )}

      <section className="card explain"><b>How points work.</b> {data.howPointsWork}{' '}
        {!admin && <>Raise yours on your <Link className="link" to="/student/placement">placement profile</Link>, with a <Link className="link" to="/student/assessments">skill assessment</Link> or a <Link className="link" to="/student/certifications">certificate</Link>.</>}
      </section>

      <div className="card table-wrap">
        <table className="table leaderboard">
          <thead><tr><th className="num">Rank</th><th>Student</th><th>Branch</th><th className="num">Résumé</th><th className="num">Verified skills</th><th className="num">Certificates</th><th>Points</th></tr></thead>
          <tbody>
            {data.top.map((e) => <Row key={e.rank} e={e} max={max} />)}
            {showYouBelow && <tr className="gap"><td colSpan={7}>…</td></tr>}
            {showYouBelow && <Row e={you} max={max} />}
          </tbody>
        </table>
      </div>
    </>
  );
}

function Row({ e, max }) {
  return (
    <tr className={e.you ? 'you' : ''}>
      <td className="num"><span className={`rank r${Math.min(e.rank, 4)}`}>{e.rank}</span></td>
      <td><b>{e.name}</b>{e.you && <span className="first-tag">You</span>}</td>
      <td>{e.branch || '-'}</td>
      <td className="num">{e.resumeScore}%</td>
      <td className="num">{e.verifiedSkills}</td>
      <td className="num">{e.verifiedCertificates}</td>
      <td><div className="bar-cell"><div className="bar"><span style={{ width: `${(e.points / max) * 100}%` }} /></div><b className="num">{e.points}</b></div></td>
    </tr>
  );
}
