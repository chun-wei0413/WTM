import type { ReportCase, ReportReason, ReviewStatus } from '../api/types';

export const REASON_LABELS: Record<ReportReason, string> = {
  WRONG_TAGS: '標籤不精確',
  WRONG_MEANING: '描述不貼切',
  NOT_A_MEME: '這不是梗圖',
  INAPPROPRIATE: '不適合放在圖庫',
  OTHER: '其他問題',
};

export const REASON_ORDER: ReportReason[] = ['WRONG_TAGS', 'WRONG_MEANING', 'NOT_A_MEME', 'INAPPROPRIATE', 'OTHER'];

export const REVIEW_LABELS: Record<ReviewStatus, string> = {
  PENDING: '排隊等影像模型',
  RUNNING: '影像模型正在重新看這張圖',
  DONE: '已完成',
  FAILED: '分析失敗',
};

export const MAX_COMMENT_LENGTH = 300;

/** How a list of words changed: what is new, what is gone, what stayed. */
interface ListDiff {
  added: string[];
  removed: string[];
  kept: string[];
}

export function diffList(before: readonly string[], after: readonly string[]): ListDiff {
  const was = new Set(before);
  const is = new Set(after);
  return {
    added: after.filter((item) => !was.has(item)),
    removed: before.filter((item) => !is.has(item)),
    kept: after.filter((item) => was.has(item)),
  };
}

/** True while the vision model is still working on, or waiting to work on, one of the cases. */
export function isReviewing(cases: readonly ReportCase[]): boolean {
  return cases.some((c) => c.review?.status === 'PENDING' || c.review?.status === 'RUNNING');
}

/** How often to ask again while the vision model is working. */
export const REPORT_POLL_MS = 4_000;

export function commentTooLong(comment: string): boolean {
  return Array.from(comment.trim()).length > MAX_COMMENT_LENGTH;
}

/** The same text apart from spacing, so a proposal that changed nothing is not shown as a change. */
export function sameText(a: string, b: string): boolean {
  return a.replace(/\s+/g, '') === b.replace(/\s+/g, '');
}
