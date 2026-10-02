import { describe, expect, it } from 'vitest';
import { isFinished, pollDelay } from './polling';

describe('pollDelay', () => {
  it('never gets quicker as a job goes on', () => {
    let previous = 0;
    for (let attempt = 0; attempt < 100; attempt++) {
      const delay = pollDelay(attempt);
      expect(delay).toBeGreaterThanOrEqual(previous);
      previous = delay;
    }
  });

  it('asks often at first and slows down later', () => {
    expect(pollDelay(0)).toBe(800);
    expect(pollDelay(15)).toBe(1_500);
    expect(pollDelay(60)).toBe(3_000);
  });
});

describe('isFinished', () => {
  it('is true only for the two end states', () => {
    expect(isFinished('COMPLETED')).toBe(true);
    expect(isFinished('FAILED')).toBe(true);
    expect(isFinished('PENDING')).toBe(false);
    expect(isFinished('RUNNING')).toBe(false);
  });
});
