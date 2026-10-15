interface Props {
  label: string;
  value?: string;
  list?: string[];
  changed?: boolean;
  /** Lines of the list that are new, to be marked. */
  added?: string[];
}

/** One field of a description: a text, or a list of lines, with what changed marked. */
export function Field({ label, value, list, changed = false, added = [] }: Props) {
  return (
    <div className={changed ? 'field field-changed' : 'field'}>
      <span className="field-label">{label}</span>
      {list ? (
        list.length === 0 ? (
          <span className="muted">(空)</span>
        ) : (
          <ul>
            {list.map((line) => (
              <li key={line} className={added.includes(line) ? 'line-added' : undefined}>
                {line}
              </li>
            ))}
          </ul>
        )
      ) : (
        <p>{value || <span className="muted">(空)</span>}</p>
      )}
    </div>
  );
}
