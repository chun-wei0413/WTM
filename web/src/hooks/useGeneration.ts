import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { GenerationView } from '../api/types';
import { isFinished, pollDelay, POLL_TIMEOUT_MS } from '../lib/polling';

export type GenerationState =
  | { phase: 'idle' }
  | { phase: 'submitting' }
  /** The job is queued or running; `job` is the latest report, or null before the first one. */
  | { phase: 'working'; job: GenerationView | null }
  | { phase: 'done'; job: GenerationView }
  | { phase: 'error'; error: unknown };

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Submits a situation, then asks for the result until it is ready. */
export function useGeneration() {
  const [state, setState] = useState<GenerationState>({ phase: 'idle' });
  // Each submission gets a number; a poll that sees a newer number knows it has been replaced and stops.
  const current = useRef(0);

  useEffect(
    () => () => {
      current.current++; // the page is going away: stop polling
    },
    [],
  );

  const submit = useCallback(async (situation: string) => {
    const run = ++current.current;
    const stale = () => current.current !== run;
    setState({ phase: 'submitting' });
    try {
      const { jobId } = await api.submitGeneration(situation);
      if (stale()) return;
      setState({ phase: 'working', job: null });

      const started = Date.now();
      for (let attempt = 0; ; attempt++) {
        await sleep(pollDelay(attempt));
        if (stale()) return;
        const job = await api.getGeneration(jobId);
        if (stale()) return;
        if (isFinished(job.status)) {
          setState({ phase: 'done', job });
          return;
        }
        setState({ phase: 'working', job });
        if (Date.now() - started > POLL_TIMEOUT_MS) {
          setState({ phase: 'error', error: new Error('The request is taking too long') });
          return;
        }
      }
    } catch (error) {
      if (!stale()) setState({ phase: 'error', error });
    }
  }, []);

  const reset = useCallback(() => {
    current.current++;
    setState({ phase: 'idle' });
  }, []);

  return { state, submit, reset };
}
