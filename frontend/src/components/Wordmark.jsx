import logoMark from '../assets/logo-mark.png';
import logoFull from '../assets/logo-full.png';

/** The CareerSphere AI symbol (C, cap and rising chart) as a small app-icon tile. */
export default function Wordmark({ size = 30 }) {
  return <img className="logo-mark" src={logoMark} width={size} height={size} alt="" aria-hidden="true" />;
}

/** The full logo with name and tagline, for large placements such as the login page. */
export function FullLogo({ width = 170 }) {
  return <img className="logo-full" src={logoFull} width={width} alt="CareerSphere AI: Learn, Plan, Place" />;
}
