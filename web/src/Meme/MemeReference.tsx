import type { Reference } from '../api/types';
import { isHttpUrl } from '../lib/collection';

/** An explanation of the meme written by its source, with what is needed to credit it. */
export function MemeReference({ reference }: { reference: Reference }) {
  return (
    <div className="tile-reference">
      <span className="tile-reference-label">來源說明</span>
      <p>{reference.text}</p>
      <p className="tile-reference-credit">
        出處:
        {reference.url && isHttpUrl(reference.url) ? (
          <a href={reference.url} target="_blank" rel="noreferrer">
            {reference.sourceName ?? '原文'}
          </a>
        ) : (
          (reference.sourceName ?? '未知')
        )}
        {reference.license && `,${reference.license}`}
      </p>
    </div>
  );
}
