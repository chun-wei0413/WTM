import { EXAMPLES, PICK_EXAMPLES } from './searchConstants';

interface Props {
  picking: boolean;
  /** What people searched for lately; the examples are shown while there are none. */
  hot: string[];
  busy: boolean;
  onChoose: (term: string) => void;
}

/** Ready-made searches to click: whole situations when picking, otherwise the hot searches or a few examples. */
export function ShortcutList({ picking, hot, busy, onChoose }: Props) {
  const shortcuts = picking ? PICK_EXAMPLES : hot.length > 0 ? hot : EXAMPLES;
  const label = picking ? '試試看' : hot.length > 0 ? '最近熱門' : '試試看';

  return (
    <div className="shortcuts">
      <span className="shortcuts-label">{label}</span>
      <ul className="shortcut-list" aria-label={picking ? '範例處境' : hot.length > 0 ? '熱門搜尋' : '範例'}>
        {shortcuts.map((term) => (
          <li key={term}>
            <button type="button" className="shortcut" onClick={() => onChoose(term)} disabled={busy}>
              {term}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
