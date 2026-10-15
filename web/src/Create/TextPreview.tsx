import { FONT_FAMILY, OUTLINE_RATIO, TEXT_STYLES, placeText, type TextBox } from '../lib/memeBoxes';

/** The text of one box as it will be exported, drawn inside the editor's SVG. */
export function TextPreview({ box, measure }: { box: TextBox; measure: (text: string, size: number) => number }) {
  const placed = placeText(box, measure);
  if (!placed) return null;
  const style = TEXT_STYLES[box.style];
  return (
    <g
      pointerEvents="none"
      fontFamily={FONT_FAMILY}
      fontWeight={800}
      fontSize={placed.fontSize}
      textAnchor="middle"
      dominantBaseline="central"
      fill={style.fill}
      stroke={style.outline}
      strokeWidth={placed.fontSize * OUTLINE_RATIO}
      strokeLinejoin="round"
      style={{ paintOrder: 'stroke fill' }}
    >
      {placed.lines.map((line, i) => (
        <text key={i} x={line.x} y={line.y}>
          {line.text}
        </text>
      ))}
    </g>
  );
}
