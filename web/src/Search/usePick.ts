import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Picked } from './searchTypes';

/** "Pick one for me": the model reads a situation and chooses a meme from the library. */
export function usePick() {
  const [picked, setPicked] = useState<Picked>({ state: 'idle' });
  // Only the newest request may show its answer; an older, slower one must not overwrite it.
  const latest = useRef(0);

  const pickFor = useCallback(async (described: string) => {
    const situation = described.trim();
    if (!situation) return;
    const ticket = ++latest.current;
    setPicked({ state: 'loading' });
    try {
      const result = await api.pick(situation);
      if (latest.current === ticket) setPicked({ state: 'ready', result });
    } catch (error) {
      if (latest.current === ticket) setPicked({ state: 'error', error });
    }
  }, []);

  const clear = useCallback(() => setPicked({ state: 'idle' }), []);

  useEffect(
    () => () => {
      latest.current = -1;
    },
    [],
  );

  return { picked, pickFor, clear };
}
