import { useEffect, useRef, useState } from 'react';
import type { Slot } from '../api/types';
import { bottomBox, nextBoxId, type TextBox } from '../lib/memeBoxes';
import type { Size } from '../lib/slotGeometry';

/**
 * The text boxes on the picture and which one is selected. `size` is the picture's size, or null while it is
 * not known yet, when no box can be added.
 */
export function useMemeBoxes(size: Size | null) {
  const [boxes, setBoxes] = useState<TextBox[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const fields = useRef(new Map<number, HTMLTextAreaElement>());
  const previousCount = useRef(0);

  // Put the cursor in the text field of a box the moment it appears, so typing can start at once.
  useEffect(() => {
    if (boxes.length > previousCount.current) {
      const newest = boxes[boxes.length - 1];
      if (newest) fields.current.get(newest.id)?.focus();
    }
    previousCount.current = boxes.length;
  }, [boxes]);

  /** A ref callback for the text field of a box, so the field can be focused when the box appears. */
  function fieldRef(id: number) {
    return (element: HTMLTextAreaElement | null) => {
      if (element) fields.current.set(id, element);
      else fields.current.delete(id);
    };
  }

  function addBox() {
    if (!size) return;
    const box = bottomBox(nextBoxId(boxes), size);
    setBoxes((current) => [...current, box]);
    setSelected(box.id);
  }

  function updateBox(id: number, change: Partial<TextBox>) {
    setBoxes((current) => current.map((b) => (b.id === id ? { ...b, ...change } : b)));
  }

  function removeBox(id: number) {
    setBoxes((current) => current.filter((b) => b.id !== id));
    setSelected((current) => (current === id ? null : current));
  }

  /** The slot editor speaks in slots; a text box is a slot with some text and a look attached. */
  const slots: Slot[] = boxes.map((b) => ({
    slotNo: b.id,
    role: '',
    maxChars: 0,
    required: true,
    x: b.x,
    y: b.y,
    width: b.width,
    height: b.height,
  }));

  /** Takes the slots the slot editor drew, moved or removed back into boxes; a new slot becomes a box with a placeholder text. */
  function onSlotsChange(next: Slot[]) {
    if (!size) return;
    setBoxes((current) =>
      next.map((slot) => {
        const rect = { x: slot.x, y: slot.y, width: slot.width, height: slot.height };
        const existing = current.find((b) => b.id === slot.slotNo);
        return existing ? { ...existing, ...rect } : { ...bottomBox(slot.slotNo, size), ...rect, text: '輸入文字' };
      }),
    );
  }

  return { boxes, selected, setSelected, slots, fieldRef, addBox, updateBox, removeBox, onSlotsChange };
}
