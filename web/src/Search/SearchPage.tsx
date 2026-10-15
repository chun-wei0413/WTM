import { useState } from 'react';
import { PickPanel } from './PickPanel';
import { SearchForm } from './SearchForm';
import { SearchResults } from './SearchResults';
import type { Mode } from './searchTypes';
import { useHotSearches } from './useHotSearches';
import { usePick } from './usePick';
import { useSearch } from './useSearch';

export function SearchPage() {
  const [mode, setMode] = useState<Mode>('search');
  const [text, setText] = useState('');
  const [situation, setSituation] = useState('');
  const { load, searched, search, showRandom } = useSearch();
  const { picked, pickFor, clear } = usePick();
  const hot = useHotSearches();

  const picking = mode === 'pick';
  const busy = picking ? picked.state === 'loading' : load.state === 'loading';

  function submit() {
    void (picking ? pickFor(situation) : search(text));
  }

  function chooseShortcut(term: string) {
    if (picking) {
      setSituation(term);
      void pickFor(term);
    } else {
      setText(term);
      void search(term);
    }
  }

  return (
    <div className="page search-page">
      <header className="masthead">
        <p className="eyebrow">WTM · 梗圖檢索</p>
        <h1>找梗圖</h1>
        <p className="lead search-lead">
          {picking
            ? '把你遇到的處境講出來,例如對方說了什麼、你想表達什麼。我會從圖庫找出最接近的一張,並說說它適不適合。'
            : '描述你遇到的情境,或是這張圖長什麼樣子、大家怎麼叫它,不用記得它的名字。'}
        </p>
      </header>

      <SearchForm
        mode={mode}
        onModeChange={setMode}
        text={text}
        onTextChange={setText}
        situation={situation}
        onSituationChange={setSituation}
        hot={hot}
        busy={busy}
        onSubmit={submit}
        onShortcut={chooseShortcut}
      />

      {picking ? (
        <PickPanel picked={picked} onClear={clear} />
      ) : (
        <SearchResults load={load} searched={searched} busy={busy} onShowRandom={() => void showRandom()} />
      )}
    </div>
  );
}
