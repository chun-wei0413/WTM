import type { KeyboardEvent } from 'react';
import type { Slot } from '../api/types';
import { moveRect, rectOf, withRect, type Size } from '../lib/slotGeometry';

interface Options {
  slots: Slot[];
  selected: number | null;
  disabled: boolean;
  imageSize: Size;
  onSelect: (slotNo: number | null) => void;
  onChange: (slots: Slot[]) => void;
}

/** Delete removes the selected slot, the arrow keys move it (Shift: ten pixels at a time). */
export function useSlotKeyboard({ slots, selected, disabled, imageSize, onSelect, onChange }: Options) {
  return function onKeyDown(event: KeyboardEvent) {
    if (disabled || selected == null) return;
    const slot = slots.find((s) => s.slotNo === selected);
    if (!slot) return;
    if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      onChange(slots.filter((s) => s.slotNo !== selected));
      onSelect(null);
      return;
    }
    const step = event.shiftKey ? 10 : 1;
    const nudge: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
    };
    const delta = nudge[event.key];
    if (delta) {
      event.preventDefault();
      onChange(withRect(slots, slot.slotNo, moveRect(rectOf(slot), delta[0], delta[1], imageSize)));
    }
  };
}
