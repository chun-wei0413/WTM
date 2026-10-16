import type { ReactNode } from 'react';
import type { LibraryItem } from '../api/types';

interface Props {
  item: LibraryItem;
  /** The download and favorite buttons. */
  actions: ReactNode;
  /** A failed download or favorite, shown next to the buttons. */
  notice?: ReactNode;
  /** Where it comes from and the report button. */
  details: ReactNode;
}

/** The words next to the large picture: the meme's name, meaning, usage, text in the picture and tags, then the buttons. */
export function ViewerText({ item, actions, notice, details }: Props) {
  // Only the search results carry these; a favorite does not.
  const usage = 'usageExamples' in item ? (item.usageExamples as string[]) : [];

  return (
    <div className="viewer-text">
      <h2>{item.name}</h2>
      {item.meaning && <p className="viewer-meaning">{item.meaning}</p>}
      {usage.length > 0 && (
        <>
          <h3>什麼時候用</h3>
          <ul className="viewer-usage">
            {usage.map((example) => (
              <li key={example}>{example}</li>
            ))}
          </ul>
        </>
      )}
      {item.imageText && item.imageText.trim() !== '' && (
        <>
          <h3>圖上的字</h3>
          <p className="viewer-image-text">{item.imageText}</p>
        </>
      )}
      {item.tags.length > 0 && (
        <ul className="tag-list" aria-label="標籤">
          {item.tags.map((tag) => (
            <li key={tag}>{tag}</li>
          ))}
        </ul>
      )}
      <div className="viewer-actions">{actions}</div>
      {notice}
      <div className="viewer-details">{details}</div>
    </div>
  );
}
