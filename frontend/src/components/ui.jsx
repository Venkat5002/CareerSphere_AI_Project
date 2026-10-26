/** Small shared UI pieces used across pages. */

export function Loader({ label = 'Loading...' }) {
  return (
    <div className="loader" role="status">
      <span className="spinner" aria-hidden="true" />
      {label}
    </div>
  );
}

export function ErrorBox({ message, onRetry }) {
  if (!message) return null;
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button className="btn btn-small btn-ghost" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function SuccessBox({ message }) {
  if (!message) return null;
  return (
    <div className="alert alert-success" role="status">
      {message}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, hint }) {
  return (
    <div className="card stat">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}

export function EmptyState({ title, children }) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      {children && <div className="muted">{children}</div>}
    </div>
  );
}

const STATUS_LABELS = {
  APPLIED: 'Applied',
  UNDER_REVIEW: 'Under review',
  SHORTLISTED: 'Shortlisted',
  INTERVIEW_SCHEDULED: 'Interview scheduled',
  SELECTED: 'Selected',
  REJECTED: 'Rejected',
  OPEN: 'Open',
  CLOSED: 'Closed',
};

export const APPLICATION_STATUSES = [
  'APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW_SCHEDULED', 'SELECTED', 'REJECTED',
];

export function statusLabel(status) {
  return STATUS_LABELS[status] || status;
}

export function StatusBadge({ status }) {
  return <span className={`badge badge-${status?.toLowerCase()}`}>{statusLabel(status)}</span>;
}

/** Colored score pill: green >= 70, amber >= 40, grey below. */
export function MatchPill({ score }) {
  const tone = score >= 70 ? 'high' : score >= 40 ? 'mid' : 'low';
  return <span className={`match-pill match-${tone}`}>{score}% match</span>;
}

/** Shows exactly why a match score is what it is. */
export function MatchBreakdown({ match }) {
  if (!match) return null;
  const gaps = Object.fromEntries((match.levelGaps || []).map((g) => [g.skill, g]));
  const nice = new Set(match.niceToHave || []);
  const tag = (s) => (nice.has(s) ? <span className="chip-tag">nice to have</span> : null);
  return (
    <div className="match-breakdown">
      <div className="skill-row">
        {match.matchedSkills.map((s) => (gaps[s] ? (
          <span key={s} className="chip chip-gap" title={`You're ${gaps[s].have}; this job asks for ${gaps[s].need}. Half credit.`}>
            ½ {s} <span className="chip-tag">{gaps[s].have} → {gaps[s].need}</span>{tag(s)}
          </span>
        ) : (
          <span key={s} className="chip chip-have" title="You have this skill at the level asked">✓ {s}{tag(s)}</span>
        )))}
        {match.missingSkills.map((s) => (
          <span key={s} className="chip chip-missing" title="You don't have this skill yet">+ {s}{tag(s)}</span>
        ))}
      </div>
      <p className="why">{match.explanation}</p>
    </div>
  );
}

/** "Java (Advanced), Spring Boot, Docker (nice to have)": a job's skills with any non-default settings. */
export function requirementText(requirements) {
  const names = ['Beginner', 'Intermediate', 'Advanced'];
  return (requirements || []).map((r) => {
    const notes = [];
    if (r.minLevel > 1) notes.push(names[r.minLevel - 1]);
    if (!r.mustHave) notes.push('nice to have');
    return notes.length ? `${r.skill} (${notes.join(', ')})` : r.skill;
  }).join(', ');
}

export function formatDate(value) {
  if (!value) return '-';
  return new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
