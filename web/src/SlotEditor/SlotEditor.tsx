import { useRef, type ReactNode } from 'react';
import type { Slot } from '../api/types';
import type { Size } from '../lib/slotGeometry';
import { SlotHandles } from './SlotHandles';
import { SlotRect } from './SlotRect';
import { useImageScale } from './useImageScale';
import { useSlotKeyboard } from './useSlotKeyboard';
import { useSlotPointer } from './useSlotPointer';

interface Props {
  imageUrl: string;
  imageSize: Size;
  slots: Slot[];
  selected: number | null;
  /** True for a retired template: slots can be looked at but not changed. */
  disabled?: boolean;
  /** Drawn on top of the picture and under the slot outlines, in image pixels (for example a text preview). */
  overlay?: ReactNode;
  /** Whether each slot shows its number. Turn off when the overlay already shows what is in the slot. */
  showLabels?: boolean;
  onSelect: (slotNo: number | null) => void;
  onChange: (slots: Slot[]) => void;
}

/**
 * Shows the template image with its caption slots drawn on top. Drag on an empty spot to draw a new
 * slot, drag a slot to move it, drag a handle to resize it. All positions are in image pixels, so they
 * are exactly what gets saved.
 */
export function SlotEditor({
  imageUrl,
  imageSize,
  slots,
  selected,
  disabled = false,
  overlay,
  showLabels = true,
  onSelect,
  onChange,
}: Props) {
  const svgRef = useRef<SVGSVGElement>(null);
  const scale = useImageScale(svgRef, imageSize.width);
  const pointer = useSlotPointer({ svgRef, imageSize, slots, disabled, onSelect, onChange });
  const onKeyDown = useSlotKeyboard({ slots, selected, disabled, imageSize, onSelect, onChange });

  return (
    <div className="slot-editor" tabIndex={0} onKeyDown={onKeyDown} aria-label="文字格編輯區">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${imageSize.width} ${imageSize.height}`}
        preserveAspectRatio="none"
        style={{ aspectRatio: `${imageSize.width} / ${imageSize.height}`, touchAction: 'none' }}
        onPointerDown={pointer.onBackgroundDown}
        onPointerMove={pointer.onMove}
        onPointerUp={pointer.onUp}
        onPointerCancel={pointer.onUp}
      >
        <image href={imageUrl} width={imageSize.width} height={imageSize.height} preserveAspectRatio="none" />
        {overlay}
        {slots.map((slot) => (
          <SlotRect
            key={slot.slotNo}
            slot={slot}
            selected={slot.slotNo === selected}
            showLabel={showLabels}
            fontScale={scale}
            onPointerDown={pointer.onSlotDown}
          />
        ))}
        {pointer.preview && <rect className="slot-rect preview" {...pointer.preview} />}
        {!disabled &&
          slots
            .filter((s) => s.slotNo === selected)
            .map((slot) => (
              <SlotHandles key={slot.slotNo} slot={slot} size={12 * scale} onPointerDown={pointer.onHandleDown} />
            ))}
      </svg>
    </div>
  );
}
