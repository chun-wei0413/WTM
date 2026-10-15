import { vi } from 'vitest';

/** jsdom has no pointer events, no layout and no ResizeObserver; this gives the slot editor a 400 x 300 picture shown at 200 pixels wide. */
export function installPicturePointer(): void {
  class FakePointerEvent extends MouseEvent {
    pointerId: number;
    constructor(type: string, init: PointerEventInit = {}) {
      super(type, init);
      this.pointerId = init.pointerId ?? 1;
    }
  }
  let captured = false;
  vi.stubGlobal('PointerEvent', FakePointerEvent);
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  Element.prototype.getBoundingClientRect = () =>
    ({ left: 0, top: 0, right: 400, bottom: 300, width: 400, height: 300, x: 0, y: 0, toJSON() {} }) as DOMRect;
  Object.defineProperty(Element.prototype, 'clientWidth', { configurable: true, get: () => 200 });
  const svg = SVGElement.prototype as unknown as Record<string, unknown>;
  svg.setPointerCapture = () => {
    captured = true;
  };
  svg.hasPointerCapture = () => captured;
  svg.releasePointerCapture = () => {
    captured = false;
  };
}
