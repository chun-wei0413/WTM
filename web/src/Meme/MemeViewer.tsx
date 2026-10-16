import { useEffect, useRef, type ReactNode } from 'react';
import type { LibraryItem } from '../api/types';
import { ViewerText } from './ViewerText';

interface Props {
  item: LibraryItem;
  onClose: () => void;
  /** The download and favorite buttons, the same ones the card has. */
  actions: ReactNode;
  /** Where it comes from and the report button. */
  details: ReactNode;
  /** A failed download or favorite, shown next to the buttons. */
  notice?: ReactNode;
}

/**
 * One meme, large: the picture at full size and everything the card has to cut short.
 * A native modal dialog, so it sits above the tilted cards, keeps the keyboard inside, and closes with Esc.
 */
export function MemeViewer({ item, onClose, actions, details, notice }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const element = dialog.current;
    if (element && !element.open) element.showModal();
    // The browser would put the focus on the close button, the first thing it finds, and ring it.
    // Start on the dialog itself instead; Tab still reaches the button.
    element?.focus();
    return () => element?.close();
  }, []);

  return (
    <dialog
      ref={dialog}
      className="meme-viewer"
      aria-label={item.name}
      tabIndex={-1}
      onClose={onClose}
      // A click on the dimmed backdrop lands on the dialog itself; one inside lands on its content.
      onClick={(event) => {
        if (event.target === event.currentTarget) dialog.current?.close();
      }}
    >
      <div className="viewer-body">
        <button type="button" className="viewer-close" onClick={() => dialog.current?.close()} aria-label="關閉">
          ✕
        </button>
        <div className="viewer-picture">
          <img src={item.imageUrl} alt={item.meaning ?? item.name} width={item.imageWidth} height={item.imageHeight} />
        </div>
        <ViewerText item={item} actions={actions} notice={notice} details={details} />
      </div>
    </dialog>
  );
}
