import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../services/api';
import { applicationService } from '../services/applicationService';
import { copilotService } from '../services/copilotService';

const STARTERS = [
  'Which skill should I learn next?',
  'Which jobs can I apply to now?',
  'Why is my match low for Java Backend Developer?',
  'What if I learn Docker and React?',
  'What is the status of my applications?',
];

/** Tiny formatter for the Copilot's replies: paragraphs, "- " bullets and **bold**. */
function Formatted({ text }) {
  const blocks = [];
  let list = null;
  text.split('\n').forEach((raw, i) => {
    const line = raw.trim();
    const bold = (s) => s.split(/\*\*(.+?)\*\*/g).map((part, k) => (k % 2 ? <b key={k}>{part}</b> : part));
    if (/^[-*•]\s+/.test(line)) {
      if (!list) { list = []; blocks.push(<ul key={`u${i}`}>{list}</ul>); }
      list.push(<li key={i}>{bold(line.replace(/^[-*•]\s+/, ''))}</li>);
    } else {
      list = null;
      if (line) blocks.push(<p key={i}>{bold(line)}</p>);
    }
  });
  return <>{blocks}</>;
}

function Action({ action, onSimulate }) {
  const navigate = useNavigate();
  const [state, setState] = useState('idle'); // idle | busy | done | error
  const [error, setError] = useState('');

  async function apply() {
    setState('busy'); setError('');
    try {
      await applicationService.apply(action.jobId);
      setState('done');
    } catch (e) {
      setError(errorMessage(e));
      setState('error');
    }
  }

  if (action.type === 'APPLY') {
    if (state === 'done') return <span className="done">Applied to {action.title}</span>;
    return (
      <>
        <button onClick={apply} disabled={state === 'busy'}>{state === 'busy' ? 'Applying...' : `Apply to ${action.title}`}</button>
        {error && <span className="msg-notice">{error}</span>}
      </>
    );
  }
  if (action.type === 'SIMULATE') {
    return <button onClick={() => onSimulate(action.skills)}>Show {action.skills.join(' + ')} on my map</button>;
  }
  if (action.type === 'OPEN_JOB') {
    return <button className="secondary" onClick={() => navigate(`/student/jobs/${action.jobId}`)}>Open {action.title}</button>;
  }
  if (action.type === 'OPEN_PAGE') {
    return <button className="secondary" onClick={() => navigate(action.link)}>{action.title}</button>;
  }
  return null;
}

/** Career Copilot rail. Answers come from the backend, which grounds them in the student's own data. */
export default function Copilot({ open, onClose }) {
  const navigate = useNavigate();
  const [status, setStatus] = useState(null);
  const [messages, setMessages] = useState([]); // { role, text, actions?, dataUsed?, notice?, mode? }
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const logRef = useRef(null);

  useEffect(() => {
    copilotService.status().then(setStatus).catch(() => setStatus({ mode: 'offline' }));
  }, []);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, busy]);

  async function send(text) {
    const question = text.trim();
    if (!question || busy) return;
    const history = [...messages, { role: 'user', text: question }];
    setMessages(history);
    setDraft('');
    setBusy(true);
    try {
      const res = await copilotService.ask(history.slice(-10).map(({ role, text: t }) => ({ role, text: t.slice(0, 2000) })));
      setMessages((m) => [...m, { role: 'assistant', text: res.reply, actions: res.actions, dataUsed: res.dataUsed, notice: res.notice, mode: res.mode }]);
    } catch (e) {
      setMessages((m) => [...m, { role: 'assistant', text: errorMessage(e), actions: [], error: true }]);
    } finally {
      setBusy(false);
    }
  }

  function simulate(skills) {
    navigate('/student/dashboard', { state: { simulate: skills, at: Date.now() } });
  }

  if (!open) return null;

  return (
    <aside className="copilot" aria-label="Career Copilot">
      <div className="copilot-head">
        <div>
          <h2>Copilot</h2>
          <div className="copilot-mode">
            {status?.mode === 'gemini'
              ? <>Powered by <b>Gemini</b>, reading your live data</>
              : <>Offline mode: built-in answers, no AI key set</>}
          </div>
        </div>
        <button className="copilot-close" onClick={onClose} aria-label="Close Copilot">Close</button>
      </div>

      <div className="copilot-log" ref={logRef} aria-live="polite">
        {messages.length === 0 && (
          <div className="copilot-intro">
            <p>Ask about your matches, skills and applications. I answer from your profile and the open jobs, and I can set up actions for you to confirm.</p>
            <div className="prompts">
              {STARTERS.map((s) => (
                <button key={s} className="prompt" onClick={() => send(s)}>{s}</button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`msg ${m.role === 'user' ? 'user' : 'bot'}`}>
            {m.role === 'user' ? m.text : <Formatted text={m.text} />}
            {m.actions?.length > 0 && (
              <div className="msg-actions">
                {m.actions.map((a, k) => <Action key={k} action={a} onSimulate={simulate} />)}
              </div>
            )}
            {m.notice && <div className="msg-notice">{m.notice}</div>}
            {m.dataUsed?.length > 0 && <div className="msg-meta">Based on {m.dataUsed.join(', ')}</div>}
          </div>
        ))}
        {busy && <div className="thinking" aria-label="Copilot is thinking"><span /><span /><span /></div>}
      </div>

      <form className="copilot-form" onSubmit={(e) => { e.preventDefault(); send(draft); }}>
        <textarea
          id="copilot-input"
          rows={2}
          value={draft}
          maxLength={2000}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(draft); } }}
          placeholder="Ask about jobs, skills or applications"
          aria-label="Message Copilot"
        />
        <button disabled={busy || !draft.trim()}>Send</button>
      </form>
    </aside>
  );
}
