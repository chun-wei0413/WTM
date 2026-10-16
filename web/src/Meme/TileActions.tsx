interface Props {
  favorite: boolean;
  downloading: boolean;
  starring: boolean;
  onDownload: () => void;
  onToggleFavorite: () => void;
}

/** The download button and the favorite button of a meme. */
export function TileActions({ favorite, downloading, starring, onDownload, onToggleFavorite }: Props) {
  return (
    <>
      <button type="button" className="button" onClick={onDownload} disabled={downloading}>
        {downloading ? '下載中…' : '下載'}
      </button>
      <button
        type="button"
        className={favorite ? 'button button-favorite active' : 'button button-favorite'}
        onClick={onToggleFavorite}
        disabled={starring}
        aria-pressed={favorite}
      >
        {favorite ? '★ 已收藏' : '☆ 收藏'}
      </button>
    </>
  );
}
