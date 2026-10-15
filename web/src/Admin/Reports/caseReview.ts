import type { ReportCase } from '../../api/types';

/** Where the model's review of a case stands: still working, and the proposal once it is done. */
export function reviewState(item: ReportCase) {
  const review = item.review;
  const working = review?.status === 'PENDING' || review?.status === 'RUNNING';
  const suggestion = review?.status === 'DONE' ? review.suggestion : null;
  return { review, working, suggestion };
}
