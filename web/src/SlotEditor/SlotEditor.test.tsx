// @vitest-environment jsdom
import { cleanup, fireEvent, render } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Slot } from '../api/types';
import { installPicturePointer } from '../testSupport/pointer';
import { SlotEditor } from './SlotEditor';

const start: Slot[] = [
  { slotNo: 1, role: 'a', maxChars: 5, required: true, x: 20, y: 20, width: 100, height: 60 },
  { slotNo: 2, role: 'b', maxChars: 5, required: false, x: 200, y: 100, width: 120, height: 80 },
];

let latest: Slot[];
let picked: Array<number | null>;

function Harness(props: { disabled?: boolean; showLabels?: boolean; overlay?: boolean }) {
  const [slots, setSlots] = useState<Slot[]>(start);
  const [selected, setSelected] = useState<number | null>(null);
  latest = slots;
  return (
    <SlotEditor
      imageUrl="http://x/i.png"
      imageSize={{ width: 400, height: 300 }}
      slots={slots}
      selected={selected}
      disabled={props.disabled}
      showLabels={props.showLabels}
      overlay={props.overlay ? <text className="overlay">hi</text> : undefined}
      onSelect={(n) => {
        picked.push(n);
        setSelected(n);
      }}
      onChange={setSlots}
    />
  );
}

function setup(props: Parameters<typeof Harness>[0] = {}) {
  const { container } = render(<Harness {...props} />);
  const svg = container.querySelector('svg')!;
  const pointer = (type: 'pointerDown' | 'pointerMove' | 'pointerUp', target: Element, x: number, y: number) =>
    fireEvent[type](target, { clientX: x, clientY: y, pointerId: 1 });
  const rects = () => Array.from(container.querySelectorAll('rect.slot-rect:not(.preview)'));
  const handles = () => Array.from(container.querySelectorAll('rect.slot-handle'));
  const keys = (key: string, shiftKey = false) => fireEvent.keyDown(container.querySelector('.slot-editor')!, { key, shiftKey });
  return { container, svg, pointer, rects, handles, keys };
}

beforeEach(() => {
  installPicturePointer();
  picked = [];
});
afterEach(cleanup);

describe('SlotEditor', () => {
  it('draws the picture and every slot, with its number', () => {
    const { container, rects } = setup();

    expect(container.querySelector('image')?.getAttribute('href')).toBe('http://x/i.png');
    expect(rects()).toHaveLength(2);
    expect(Array.from(container.querySelectorAll('.slot-label')).map((t) => t.textContent)).toEqual(['1', '2']);
    expect(rects()[1]?.classList.contains('optional')).toBe(true);
  });

  it('leaves the numbers out when asked, and shows what is drawn over the picture', () => {
    const { container } = setup({ showLabels: false, overlay: true });

    expect(container.querySelectorAll('.slot-label')).toHaveLength(0);
    expect(container.querySelector('.overlay')).not.toBeNull();
  });

  it('draws a new slot where the visitor drags on an empty spot, and selects it', () => {
    const { svg, pointer, container } = setup();

    pointer('pointerDown', svg, 250, 20);
    pointer('pointerMove', svg, 380, 90);
    expect(container.querySelector('rect.preview')).not.toBeNull();
    pointer('pointerUp', svg, 380, 90);

    expect(container.querySelector('rect.preview')).toBeNull();
    expect(latest).toHaveLength(3);
    expect(latest[2]).toEqual({ slotNo: 3, role: '文字格 3', maxChars: 5, required: true, x: 250, y: 20, width: 130, height: 70 });
    expect(picked.at(-1)).toBe(3);
  });

  it('does not make a slot of a click or of a drag that is too small', () => {
    const { svg, pointer } = setup();

    pointer('pointerDown', svg, 5, 250);
    pointer('pointerUp', svg, 8, 252);
    pointer('pointerDown', svg, 5, 250);
    pointer('pointerMove', svg, 15, 255);
    pointer('pointerUp', svg, 15, 255);

    expect(latest).toEqual(start);
  });

  it('selects a slot and moves it with the pointer', () => {
    const { svg, pointer, rects } = setup();

    pointer('pointerDown', rects()[0]!, 40, 40);
    expect(picked.at(-1)).toBe(1);
    pointer('pointerMove', svg, 90, 100);
    pointer('pointerUp', svg, 90, 100);

    expect(latest[0]).toMatchObject({ x: 70, y: 80, width: 100, height: 60 });
    expect(latest[1]).toEqual(start[1]);
  });

  it('keeps a moved slot inside the picture', () => {
    const { svg, pointer, rects } = setup();

    pointer('pointerDown', rects()[0]!, 40, 40);
    pointer('pointerMove', svg, 4000, 4000);
    pointer('pointerUp', svg, 4000, 4000);

    expect(latest[0]).toMatchObject({ x: 300, y: 240 });
  });

  it('shows handles only around the selected slot, and resizes with one', () => {
    const { svg, pointer, rects, handles } = setup();
    expect(handles()).toHaveLength(0);
    pointer('pointerDown', rects()[0]!, 40, 40);
    pointer('pointerUp', svg, 40, 40);
    expect(handles()).toHaveLength(8);

    pointer('pointerDown', handles()[4]!, 120, 80); // south-east corner
    pointer('pointerMove', svg, 170, 110);
    pointer('pointerUp', svg, 170, 110);

    expect(latest[0]).toMatchObject({ x: 20, y: 20, width: 150, height: 90 });
  });

  it('removes the selected slot with Delete or Backspace', () => {
    const { svg, pointer, rects, keys } = setup();
    pointer('pointerDown', rects()[1]!, 250, 120);
    pointer('pointerUp', svg, 250, 120);

    keys('Delete');

    expect(latest.map((s) => s.slotNo)).toEqual([1]);
    expect(picked.at(-1)).toBeNull();

    pointer('pointerDown', rects()[0]!, 40, 40);
    pointer('pointerUp', svg, 40, 40);
    keys('Backspace');
    expect(latest).toEqual([]);
  });

  it('nudges the selected slot with the arrow keys, ten pixels with Shift', () => {
    const { svg, pointer, rects, keys } = setup();
    pointer('pointerDown', rects()[0]!, 40, 40);
    pointer('pointerUp', svg, 40, 40);

    keys('ArrowRight');
    keys('ArrowDown', true);
    keys('ArrowLeft');
    keys('ArrowUp', true);
    keys('a');

    expect(latest[0]).toMatchObject({ x: 20, y: 20 });
    keys('ArrowRight', true);
    expect(latest[0]).toMatchObject({ x: 30, y: 20 });
  });

  it('ignores the keys when nothing is selected', () => {
    const { keys } = setup();

    keys('Delete');
    keys('ArrowRight');

    expect(latest).toEqual(start);
  });

  it('can be looked at but not changed when disabled', () => {
    const { svg, pointer, rects, handles, keys } = setup({ disabled: true });

    pointer('pointerDown', svg, 250, 20);
    pointer('pointerMove', svg, 380, 90);
    pointer('pointerUp', svg, 380, 90);
    pointer('pointerDown', rects()[0]!, 40, 40);
    pointer('pointerMove', svg, 90, 100);
    pointer('pointerUp', svg, 90, 100);
    keys('Delete');
    keys('ArrowRight');

    expect(picked).toEqual([1]); // a slot can still be selected, to read it
    expect(handles()).toHaveLength(0);
    expect(latest).toEqual(start);
  });

  it('scales the handles with how small the picture is shown', () => {
    const { svg, pointer, rects, handles } = setup();
    pointer('pointerDown', rects()[0]!, 40, 40);
    pointer('pointerUp', svg, 40, 40);

    // shown at 200 pixels for 400 image pixels, so a 12 pixel handle covers 24 image pixels
    expect(handles()[0]?.getAttribute('width')).toBe('24');
  });
});

describe('SlotEditor without a pointer', () => {
  it('does not throw while the picture is not laid out yet', () => {
    vi.stubGlobal('ResizeObserver', class {
      observe() {}
      unobserve() {}
      disconnect() {}
    });

    expect(() => render(<Harness />)).not.toThrow();
  });
});
