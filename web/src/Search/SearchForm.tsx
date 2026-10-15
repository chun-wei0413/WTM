import type { FormEvent, KeyboardEvent } from 'react';
import { MAX_LENGTH, PICK_MAX_LENGTH } from './searchConstants';
import { ModeSwitch } from './ModeSwitch';
import { ShortcutList } from './ShortcutList';
import type { Mode } from './searchTypes';

interface Props {
  mode: Mode;
  onModeChange: (mode: Mode) => void;
  text: string;
  onTextChange: (text: string) => void;
  situation: string;
  onSituationChange: (situation: string) => void;
  hot: string[];
  busy: boolean;
  /** Search for the text, or pick a meme for the situation, depending on the mode. */
  onSubmit: () => void;
  onShortcut: (term: string) => void;
}

/** The box to describe what to look for, with the mode switch above it and the shortcuts below. */
export function SearchForm(props: Props) {
  const { mode, text, situation, hot, busy } = props;
  const picking = mode === 'pick';

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    props.onSubmit();
  }

  /** Enter sends the situation, Shift+Enter starts a new line; Enter that confirms a Chinese word does neither. */
  function handleSituationKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (!busy) props.onSubmit();
    }
  }

  return (
    <form onSubmit={handleSubmit} className="search-form" role="search">
      <ModeSwitch mode={mode} onChange={props.onModeChange} />

      {picking ? (
        <>
          <label className="visually-hidden" htmlFor="situation-box">
            你遇到的處境
          </label>
          <div className="search-row search-row-tall">
            <textarea
              id="situation-box"
              value={situation}
              onChange={(e) => props.onSituationChange(e.target.value)}
              onKeyDown={handleSituationKeyDown}
              maxLength={PICK_MAX_LENGTH}
              rows={2}
              placeholder="朋友一直說我 over react,幫我挑一張回他的梗圖"
              autoFocus
            />
            <button type="submit" className="search-submit" disabled={busy || !situation.trim()}>
              幫我挑 <span aria-hidden="true">→</span>
            </button>
          </div>
        </>
      ) : (
        <>
          <label className="visually-hidden" htmlFor="search-box">
            你想找的梗圖
          </label>
          <div className="search-row">
            <input
              id="search-box"
              type="search"
              value={text}
              onChange={(e) => props.onTextChange(e.target.value)}
              maxLength={MAX_LENGTH}
              placeholder="聽不懂對方剛剛在說什麼"
              autoFocus
            />
            <button type="submit" className="search-submit" disabled={busy || !text.trim()}>
              搜尋 <span aria-hidden="true">→</span>
            </button>
          </div>
        </>
      )}

      <ShortcutList picking={picking} hot={hot} busy={busy} onChoose={props.onShortcut} />
    </form>
  );
}
