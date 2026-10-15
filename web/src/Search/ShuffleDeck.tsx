/** A little deck of stickers being shuffled, to look at while the model reads the situation. Purely decorative. */
export function ShuffleDeck() {
  return (
    <div className="shuffle-deck" aria-hidden="true">
      {['?', '!', '哈', '…'].map((mark) => (
        <span key={mark} className="shuffle-card">
          {mark}
        </span>
      ))}
    </div>
  );
}
