import type { Mode } from './searchTypes';

/** The two ways to find a meme: search the library, or let the model pick one. */
export function ModeSwitch({ mode, onChange }: { mode: Mode; onChange: (mode: Mode) => void }) {
  const picking = mode === 'pick';
  return (
    <div className="mode-switch" role="tablist" aria-label="找梗圖的方式">
      <button type="button" role="tab" aria-selected={!picking} className="mode-tab" onClick={() => onChange('search')}>
        搜尋
      </button>
      <button type="button" role="tab" aria-selected={picking} className="mode-tab" onClick={() => onChange('pick')}>
        幫我挑一張
      </button>
    </div>
  );
}
