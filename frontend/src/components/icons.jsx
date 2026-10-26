/** Small line icons for the sidebar (18 px, stroke = currentColor). */
const PATHS = {
  map: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-5a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0-3.5a.5.5 0 1 0 0-1 .5.5 0 0 0 0 1Z',
  id: 'M4 5h16v14H4zM8 10.5a2 2 0 1 0 4 0 2 2 0 0 0-4 0M6.5 16c.6-1.6 2-2.5 3.5-2.5s2.9.9 3.5 2.5M15 10h3M15 13h3',
  briefcase: 'M4 8h16v11H4zM9 8V5h6v3M4 13h16',
  list: 'M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01',
  sigma: 'M18 5H6l6 7-6 7h12',
  check: 'M9 4h6v3H9zM6 6h3M15 6h3v14H6V6M9.5 14l2 2 3.5-4',
  award: 'M12 15a5 5 0 1 0 0-10 5 5 0 0 0 0 10ZM8.5 13.5 7 21l5-3 5 3-1.5-7.5',
  trophy: 'M8 4h8v5a4 4 0 0 1-8 0V4ZM8 6H5v1a3 3 0 0 0 3 3M16 6h3v1a3 3 0 0 1-3 3M12 13v4M9 20h6M10 17h4',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM5 20c1.2-3.5 3.9-5 7-5s5.8 1.5 7 5',
  chart: 'M5 20V10M12 20V4M19 20v-7',
  plus: 'M12 5v14M5 12h14',
  building: 'M5 21V4h10v17M15 9h4v12M8 8h2M8 12h2M8 16h2M3 21h18',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  shield: 'M12 3 5 6v5c0 4.5 3 8 7 10 4-2 7-5.5 7-10V6l-7-3ZM9 12l2 2 4-4',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM16 16l4 4',
  route: 'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM18 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM8 17h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7',
  menu: 'M4 7h16M4 12h16M4 17h16',
  logout: 'M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10',
};

export default function Icon({ name, size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={PATHS[name] || PATHS.list} />
    </svg>
  );
}
