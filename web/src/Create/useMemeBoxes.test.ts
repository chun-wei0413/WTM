// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Slot } from '../api/types';
import { TEXT_STYLE_IDS } from '../lib/memeBoxes';
import { useMemeBoxes } from './useMemeBoxes';

const SIZE = { width: 400, height: 300 };

afterEach(cleanup);

describe('useMemeBoxes', () => {
  it('starts with no boxes and nothing selected', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));

    expect(result.current.boxes).toEqual([]);
    expect(result.current.slots).toEqual([]);
    expect(result.current.selected).toBeNull();
  });

  it('adds a box along the bottom of the picture and selects it', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));

    act(() => result.current.addBox());

    expect(result.current.boxes).toHaveLength(1);
    const box = result.current.boxes[0]!;
    expect(box.id).toBe(1);
    expect(box.y + box.height).toBeLessThanOrEqual(SIZE.height);
    expect(box.y).toBeGreaterThan(SIZE.height / 2);
    expect(result.current.selected).toBe(box.id);
  });

  it('numbers the boxes one after the other', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));

    act(() => result.current.addBox());
    act(() => result.current.addBox());

    expect(result.current.boxes.map((b) => b.id)).toEqual([1, 2]);
  });

  it('adds nothing while the size of the picture is not known', () => {
    const { result } = renderHook(() => useMemeBoxes(null));

    act(() => result.current.addBox());
    act(() => result.current.onSlotsChange([{ slotNo: 1, role: '', maxChars: 0, required: true, x: 0, y: 0, width: 50, height: 50 }]));

    expect(result.current.boxes).toEqual([]);
  });

  it('changes one box and leaves the others', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));
    act(() => result.current.addBox());
    act(() => result.current.addBox());

    act(() => result.current.updateBox(2, { text: 'hello', style: TEXT_STYLE_IDS[1] }));

    expect(result.current.boxes[1]).toMatchObject({ text: 'hello' });
    expect(result.current.boxes[0]?.text).not.toBe('hello');
  });

  it('removes a box, and drops the selection only when it was that box', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));
    act(() => result.current.addBox());
    act(() => result.current.addBox()); // box 2 is selected

    act(() => result.current.removeBox(1));
    expect(result.current.boxes.map((b) => b.id)).toEqual([2]);
    expect(result.current.selected).toBe(2);

    act(() => result.current.removeBox(2));
    expect(result.current.boxes).toEqual([]);
    expect(result.current.selected).toBeNull();
  });

  it('shows each box to the slot editor as a slot at the same place', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));
    act(() => result.current.addBox());

    const box = result.current.boxes[0]!;
    expect(result.current.slots).toEqual([
      { slotNo: box.id, role: '', maxChars: 0, required: true, x: box.x, y: box.y, width: box.width, height: box.height },
    ]);
  });

  it('moves a box when its slot is moved, and keeps its text', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));
    act(() => result.current.addBox());
    act(() => result.current.updateBox(1, { text: 'keep me' }));
    const moved: Slot = { ...result.current.slots[0]!, x: 11, y: 12, width: 130, height: 40 };

    act(() => result.current.onSlotsChange([moved]));

    expect(result.current.boxes[0]).toMatchObject({ id: 1, text: 'keep me', x: 11, y: 12, width: 130, height: 40 });
  });

  it('turns a slot drawn on the picture into a box with a placeholder text', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));
    const drawn: Slot = { slotNo: 7, role: '', maxChars: 0, required: true, x: 10, y: 20, width: 100, height: 50 };

    act(() => result.current.onSlotsChange([drawn]));

    expect(result.current.boxes).toHaveLength(1);
    expect(result.current.boxes[0]).toMatchObject({ id: 7, text: '輸入文字', x: 10, y: 20, width: 100, height: 50 });
  });

  it('drops a box whose slot was deleted on the picture', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));
    act(() => result.current.addBox());
    act(() => result.current.addBox());

    act(() => result.current.onSlotsChange([result.current.slots[1]!]));

    expect(result.current.boxes.map((b) => b.id)).toEqual([2]);
  });

  it('puts the cursor in the text field of a box when it appears, and not when another is changed', () => {
    const { result } = renderHook(() => useMemeBoxes(SIZE));
    const field = document.createElement('textarea');
    document.body.appendChild(field);
    field.focus = vi.fn();
    // the field of box 1 is registered before the box exists, as React does while it renders the list
    result.current.fieldRef(1)(field);

    act(() => result.current.addBox());
    expect(field.focus).toHaveBeenCalledTimes(1);

    act(() => result.current.updateBox(1, { text: 'typing' }));
    expect(field.focus).toHaveBeenCalledTimes(1);

    result.current.fieldRef(1)(null);
    field.remove();
  });
});
