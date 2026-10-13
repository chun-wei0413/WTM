import { useState } from 'react';
import type { LibraryItem } from '../api/types';
import { attributionNote, isHttpUrl, sourceTypeLabel } from '../lib/collection';
import { ReportForm } from './ReportForm';

interface Props {
  item: LibraryItem;
  /** Whether this meme has been reported from this page, so the card and the big view agree. */
  reported: boolean;
  onReported: () => void;
}

/** Where a meme comes from, and the button to report it. Shown on a card under "詳情" and in the big view. */
export function MemeDetails({ item, reported, onReported }: Props) {
  const [reporting, setReporting] = useState(false);
  const source = sourceTypeLabel(item.sourceType);
  const note = attributionNote(source, item.attribution);

  return (
    <>
      {item.reference && (
        <div className="tile-reference">
          <span className="tile-reference-label">來源說明</span>
          <p>{item.reference.text}</p>
          <p className="tile-reference-credit">
            出處:
            {item.reference.url && isHttpUrl(item.reference.url) ? (
              <a href={item.reference.url} target="_blank" rel="noreferrer">
                {item.reference.sourceName ?? '原文'}
              </a>
            ) : (
              (item.reference.sourceName ?? '未知')
            )}
            {item.reference.license && `,${item.reference.license}`}
          </p>
        </div>
      )}
      {(source || note) && (
        <span className="muted">
          來源:
          {item.sourceUrl && isHttpUrl(item.sourceUrl) ? (
            <a href={item.sourceUrl} target="_blank" rel="noreferrer">
              {source ?? '原始網址'}
            </a>
          ) : (
            source
          )}
          {note && `(${note})`}
        </span>
      )}
      <div>
        <button
          type="button"
          className="button button-quiet"
          onClick={() => setReporting((open) => !open)}
          disabled={reported}
          aria-expanded={reporting}
          title="標籤、描述不精確,或這不是梗圖?告訴管理員"
        >
          {reported ? '已回報' : '回報問題'}
        </button>
      </div>
      {reporting && !reported && (
        <ReportForm
          templateId={item.templateId}
          onSent={() => {
            setReporting(false);
            onReported();
          }}
          onCancel={() => setReporting(false)}
        />
      )}
      {reported && (
        <p className="notice notice-success" role="status">
          已回報,謝謝你!管理員會查看,並請影像模型重新看這張圖。
        </p>
      )}
    </>
  );
}
