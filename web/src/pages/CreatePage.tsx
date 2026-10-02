import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { ErrorNotice } from '../components/ErrorNotice';
import { MemeCard } from '../components/MemeCard';
import { useGeneration } from '../hooks/useGeneration';

const MAX_LENGTH = 300;

const EXAMPLES = [
  '老闆臨時又改需求,我還是假裝沒事繼續工作',
  '手上專案還沒做完,又看到新技術想去玩',
  '今天終於準時下班了',
  '要存錢還是買新耳機,好糾結',
  '我把客人的抱怨當成讚美回覆了',
];

export function CreatePage() {
  const [situation, setSituation] = useState('');
  const [kept, setKept] = useState<ReadonlySet<string>>(new Set());
  const { state, submit, reset } = useGeneration();

  const busy = state.phase === 'submitting' || state.phase === 'working';
  const length = Array.from(situation).length;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!busy && situation.trim()) {
      setKept(new Set());
      void submit(situation.trim());
    }
  }

  async function keep(memeId: string) {
    await api.keepMeme(memeId);
    setKept((previous) => new Set(previous).add(memeId));
  }

  return (
    <div className="page">
      <h1>產生梗圖</h1>
      <p className="lead">用一兩句話描述你遇到的情境,系統會挑出合適的梗圖模板,為你寫好文案。</p>

      <form onSubmit={onSubmit} className="form create-form">
        <label>
          你的情境
          <textarea
            value={situation}
            onChange={(e) => setSituation(e.target.value)}
            rows={3}
            maxLength={MAX_LENGTH}
            placeholder="例如:老闆臨時又改需求,我還是假裝沒事繼續工作"
            disabled={busy}
          />
          <span className="hint counter">
            {length} / {MAX_LENGTH}
          </span>
        </label>

        <div className="chips" aria-label="範例情境">
          {EXAMPLES.map((example) => (
            <button key={example} type="button" className="chip" onClick={() => setSituation(example)} disabled={busy}>
              {example}
            </button>
          ))}
        </div>

        <div>
          <button type="submit" className="button button-primary" disabled={busy || !situation.trim()}>
            {busy ? '產生中…' : '產生梗圖'}
          </button>
        </div>
      </form>

      {state.phase === 'submitting' && <p className="status">送出中…</p>}

      {state.phase === 'working' && (
        <div className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>{state.job?.status === 'RUNNING' ? '正在挑模板、寫文案、畫圖…' : '排隊中…'}(通常需要幾秒到十幾秒)</span>
        </div>
      )}

      {state.phase === 'error' && (
        <div className="stack">
          <ErrorNotice error={state.error} />
          <button type="button" className="button" onClick={reset}>
            知道了
          </button>
        </div>
      )}

      {state.phase === 'done' && state.job.status === 'FAILED' && (
        <div className="stack">
          <div className="notice notice-error" role="alert">
            <strong>這次沒能產生梗圖</strong>
            {state.job.failureReason && <span className="notice-detail">{state.job.failureReason}</span>}
          </div>
          <div>
            <button type="button" className="button" onClick={() => void submit(state.job.situation)}>
              再試一次
            </button>
          </div>
        </div>
      )}

      {state.phase === 'done' && state.job.status === 'COMPLETED' && (
        <section aria-live="polite">
          <h2>為「{state.job.situation}」找到 {state.job.candidates.length} 張候選</h2>
          <div className="grid">
            {state.job.candidates.map((c) => (
              <MemeCard
                key={c.memeId}
                memeId={c.memeId}
                imageUrl={c.imageUrl}
                templateName={c.templateName}
                captions={c.captions}
                kept={kept.has(c.memeId) || c.status === 'KEPT'}
                onKeep={() => keep(c.memeId)}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
