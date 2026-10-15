import { describe, expect, it } from 'vitest';
import { reviewState } from './caseReview';
import { doneReview, reportCase } from './reportFixtures';

describe('reviewState', () => {
  it('has nothing while the model has not looked at the case', () => {
    expect(reviewState(reportCase({ review: null }))).toEqual({ review: null, working: false, suggestion: null });
  });

  it('is working while the review waits or runs', () => {
    for (const status of ['PENDING', 'RUNNING'] as const) {
      const state = reviewState(reportCase({ review: { status, suggestion: null, error: null } }));
      expect(state.working).toBe(true);
      expect(state.suggestion).toBeNull();
    }
  });

  it('has the proposal once the review is done', () => {
    const review = doneReview();

    const state = reviewState(reportCase({ review }));

    expect(state.working).toBe(false);
    expect(state.suggestion).toBe(review.suggestion);
  });

  it('has no proposal when the review failed', () => {
    const state = reviewState(reportCase({ review: { status: 'FAILED', suggestion: null, error: 'down' } }));

    expect(state.working).toBe(false);
    expect(state.suggestion).toBeNull();
  });
});
