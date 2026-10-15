import type { ReactNode } from 'react';

interface Props {
  title: ReactNode;
  actionLabel: string;
  onAction: () => void;
  disabled: boolean;
}

/** The title above an answer, with the one action that goes with it (refresh, clear). */
export function ResultsHeader({ title, actionLabel, onAction, disabled }: Props) {
  return (
    <div className="section-header results-head">
      <h2>{title}</h2>
      <div className="actions">
        <button type="button" className="text-action" onClick={onAction} disabled={disabled}>
          {actionLabel}
        </button>
      </div>
    </div>
  );
}
