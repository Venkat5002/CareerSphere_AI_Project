import { useEffect, useMemo, useState } from 'react';
import { authService } from '../services/authService';

export const LEVELS = ['Beginner', 'Intermediate', 'Advanced'];
export const levelName = (n) => LEVELS[(n || 1) - 1] || 'Beginner';

/** What-if key for an Opportunity Math row: "Docker" (learn it) or "Java:2" (reach Intermediate). */
export const simKey = (o) => (o.targetLevel > 1 || o.currentLevel > 0 ? `${o.skill}:${o.targetLevel}` : o.skill);

/** "Learn Docker", "Learn Docker to Intermediate" or "Level up Java to Advanced". */
export function actionLabel(o) {
  if (o.currentLevel > 0) return `Level up ${o.skill} to ${levelName(o.targetLevel)}`;
  return o.targetLevel > 1 ? `Learn ${o.skill} to ${levelName(o.targetLevel)}` : `Learn ${o.skill}`;
}

/** Turns a what-if key back into words: "Java:2" -> "Java (Intermediate)". */
export function simLabel(key) {
  const m = /^(.*):([1-3])$/.exec(key);
  return m ? `${m[1]} (${levelName(Number(m[2]))})` : key;
}

/** Looks up a level by skill name, ignoring case. */
export function levelOf(levels, name) {
  if (!levels) return 1;
  const key = Object.keys(levels).find((k) => k.toLowerCase() === name.toLowerCase());
  return key ? levels[key] : 1;
}

/**
 * Tag-style skill picker with suggestions from the backend skill catalog.
 * Press Enter or comma to add; click x to remove.
 *
 * Optional extras on each chip:
 *  - levels / onLevelsChange: a Beginner / Intermediate / Advanced switch (click to change).
 *  - nice / onNiceChange: a Must-have / Nice-to-have switch (recruiters).
 */
export default function SkillInput({
  value, onChange, placeholder = 'Type a skill and press Enter',
  levels, onLevelsChange, levelPrefix = '', nice, onNiceChange,
}) {
  const [catalog, setCatalog] = useState([]);
  const [text, setText] = useState('');

  useEffect(() => {
    authService.skills().then(setCatalog).catch(() => setCatalog([]));
  }, []);

  const suggestions = useMemo(() => {
    const q = text.trim().toLowerCase();
    if (!q) return [];
    const chosen = new Set(value.map((v) => v.toLowerCase()));
    return catalog
      .filter((s) => s.name.toLowerCase().includes(q) && !chosen.has(s.name.toLowerCase()))
      .slice(0, 6);
  }, [text, catalog, value]);

  function add(name) {
    const clean = name.trim().replace(/\s+/g, ' ');
    if (!clean) return;
    // Use the catalog spelling when it exists, so "java" becomes "Java".
    const known = catalog.find((s) => s.name.toLowerCase() === clean.toLowerCase());
    const finalName = known ? known.name : clean;
    if (!value.some((v) => v.toLowerCase() === finalName.toLowerCase())) onChange([...value, finalName]);
    setText('');
  }

  function remove(name) {
    onChange(value.filter((v) => v !== name));
    if (levels && onLevelsChange && name in levels) {
      const { [name]: _gone, ...rest } = levels;
      onLevelsChange(rest);
    }
    if (nice && onNiceChange) onNiceChange(nice.filter((n) => n !== name));
  }

  function onKeyDown(e) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      const typed = text.trim().toLowerCase();
      const exact = catalog.some((s) => s.name.toLowerCase() === typed);
      // "Spr" + Enter picks "Spring Boot"; an exact or brand-new skill is added as typed.
      add(!exact && suggestions.length > 0 ? suggestions[0].name : text);
    } else if (e.key === 'Backspace' && !text && value.length) {
      remove(value[value.length - 1]);
    }
  }

  return (
    <div className="skill-input">
      <div className="skill-row">
        {value.map((s) => {
          const lvl = levelOf(levels, s);
          const isNice = nice?.some((n) => n.toLowerCase() === s.toLowerCase());
          return (
            <span key={s} className={`chip chip-edit${isNice ? ' chip-nice' : ''}`}>
              {s}
              {onLevelsChange && (
                <button
                  type="button"
                  className={`lvl lvl-${lvl}`}
                  title="Click to change the level"
                  aria-label={`${s}: ${levelPrefix}${levelName(lvl)}. Click to change.`}
                  onClick={() => onLevelsChange({ ...(levels || {}), [s]: (lvl % 3) + 1 })}
                >
                  <span className="lvl-dots" aria-hidden="true">
                    {[1, 2, 3].map((d) => <i key={d} className={d <= lvl ? 'on' : ''} />)}
                  </span>
                  {levelPrefix}{levelName(lvl)}
                </button>
              )}
              {onNiceChange && (
                <button
                  type="button"
                  className={`need ${isNice ? 'need-nice' : 'need-must'}`}
                  title="Click to switch between must-have and nice to have"
                  aria-label={`${s} is ${isNice ? 'nice to have' : 'a must-have'}. Click to switch.`}
                  onClick={() => onNiceChange(isNice
                    ? nice.filter((n) => n.toLowerCase() !== s.toLowerCase())
                    : [...(nice || []), s])}
                >
                  {isNice ? 'Nice to have' : 'Must-have'}
                </button>
              )}
              <button type="button" aria-label={`Remove ${s}`} onClick={() => remove(s)}>
                ×
              </button>
            </span>
          );
        })}
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={value.length ? '' : placeholder}
          aria-label="Add skill"
        />
      </div>
      {suggestions.length > 0 && (
        <ul className="suggestions">
          {suggestions.map((s) => (
            <li key={s.name}>
              <button type="button" onMouseDown={(e) => { e.preventDefault(); add(s.name); }}>
                {s.name} <span className="muted">{s.category}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
