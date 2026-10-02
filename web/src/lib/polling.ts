import type { GenerationStatus } from '../api/types';

/** How long to wait before asking again: quick at first, then gentler the longer a job takes. */
export function pollDelay(attempt: number): number {
  if (attempt < 10) return 800;
  if (attempt < 30) return 1_500;
  return 3_000;
}

export const POLL_TIMEOUT_MS = 120_000;

export function isFinished(status: GenerationStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED';
}
