import { useEffect, useState, type RefObject } from 'react';

/** How many image pixels one screen pixel covers, so handles and labels keep a readable size. */
export function useImageScale(svgRef: RefObject<SVGSVGElement | null>, imageWidth: number): number {
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    const update = () => setScale(svg.clientWidth > 0 ? imageWidth / svg.clientWidth : 1);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(svg);
    return () => observer.disconnect();
  }, [svgRef, imageWidth]);

  return scale;
}
