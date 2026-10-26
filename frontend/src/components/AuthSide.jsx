import { FullLogo } from './Wordmark';

/** Left panel of the login and register pages: the full logo, the map motif and a short pitch. */
export default function AuthSide({ title, text }) {
  const rings = [44, 90, 136, 182];
  const dots = [
    [0.6, 70, true], [1.5, 120, false], [2.3, 64, true], [3.1, 150, false],
    [3.9, 100, false], [4.6, 58, true], [5.4, 170, false],
  ];
  return (
    <div className="auth-side">
      <FullLogo width={150} />
      <svg className="auth-map" viewBox="0 0 400 400" aria-hidden="true">
        {rings.map((r) => <circle key={r} cx="200" cy="200" r={r} fill="none" stroke="#9fb0c9" strokeOpacity=".35" />)}
        <circle cx="200" cy="200" r="90" fill="#e5a019" fillOpacity=".12" stroke="#e5a019" strokeWidth="2.5" />
        {dots.map(([a, r, reach], i) => (
          <circle key={i} cx={200 + r * Math.cos(a)} cy={200 + r * Math.sin(a)} r="7"
            fill={reach ? '#e9eef6' : '#13284a'} stroke="#e9eef6" strokeWidth="2" />
        ))}
        <circle cx="200" cy="200" r="30" fill="#e9eef6" />
        <text x="200" y="205" textAnchor="middle" fill="#13284a" style={{ font: '700 14px var(--font-display)' }}>You</text>
      </svg>
      <div>
        <h2>{title}</h2>
        <p>{text}</p>
      </div>
    </div>
  );
}
