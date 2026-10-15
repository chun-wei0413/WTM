/** One number of the library, with its name; `warn` marks a number that should be zero. */
export function Stat({ label, value, warn = false }: { label: string; value: number; warn?: boolean }) {
  return (
    <div className={warn ? 'stat stat-warn' : 'stat'}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
