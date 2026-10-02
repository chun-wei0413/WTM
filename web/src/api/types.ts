/** Shapes of the JSON the server sends and receives. */

export interface LoginResponse {
  token: string;
  expiresAt: string;
}

export interface Slot {
  slotNo: number;
  role: string;
  maxChars: number;
  required: boolean;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface MemeProfile {
  meaning: string;
  usageExamples: string[];
  emotions: string[];
  aliases: string[];
  /** Words that appear in the picture itself. */
  imageText: string;
  /** Keywords: what is shown, the topic, the joke format. */
  tags: string[];
}

export type TemplateStatus = 'DRAFT' | 'APPROVED' | 'RETIRED';

export interface TemplateSummary {
  id: string;
  name: string;
  status: TemplateStatus;
  version: number;
  imageUrl: string;
  updatedAt: string;
  meaning: string | null;
  tags: string[];
  sourceType: string | null;
  attribution: string | null;
}

export interface TemplateView {
  id: string;
  name: string;
  status: TemplateStatus;
  version: number;
  imageWidth: number;
  imageHeight: number;
  imageUrl: string;
  profile: MemeProfile;
  slots: Slot[];
  createdAt: string;
  updatedAt: string;
}

/** One meme of the library, as the browsing pages show it. */
export interface LibraryItem {
  templateId: string;
  name: string;
  imageUrl: string;
  /** The picture's size in pixels, so a page can leave room for it before it has loaded. */
  imageWidth: number;
  imageHeight: number;
  /** What the picture means, as written by the vision model or an administrator. */
  meaning: string | null;
  tags: string[];
  /** Text that appears in the picture itself. */
  imageText: string | null;
  /** IMGFLIP, WIKIMEDIA, PTT, UPLOAD, URL, INBOX … ; null for hand-made templates. */
  sourceType: string | null;
  sourceUrl: string | null;
  attribution: string | null;
}

export interface SearchResult extends LibraryItem {
  score: number;
  slots: Slot[];
}

export interface HotSearch {
  term: string;
  searches: number;
}

export interface IndexSyncResult {
  indexed: number;
  removed: number;
  failed: number;
}

export interface LibraryStats {
  total: number;
  approved: number;
  retired: number;
  waitingForTags: number;
  beingTagged: number;
  tagFailures: number;
}

export type IngestStatus = 'IMPORTED' | 'DUPLICATE' | 'REJECTED';

/** What happened to one picture offered to the library. */
export interface IngestResult {
  fileName: string;
  status: IngestStatus;
  templateId: string | null;
  reason: string | null;
}

export interface SourceOption {
  key: string;
  label: string;
  defaultValue: string;
}

export interface CollectionSource {
  id: string;
  name: string;
  description: string;
  options: SourceOption[];
}

export interface RunCounts {
  found: number;
  imported: number;
  duplicates: number;
  rejected: number;
  failed: number;
}

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface CollectionRun {
  id: string;
  source: string;
  options: string | null;
  status: RunStatus;
  counts: RunCounts;
  message: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export type ReportReason = 'WRONG_TAGS' | 'WRONG_MEANING' | 'NOT_A_MEME' | 'INAPPROPRIATE' | 'OTHER';

/** What the vision model proposes after looking at a reported meme again. */
export interface ReportSuggestion {
  isMeme: boolean;
  meaning: string;
  usageExamples: string[];
  emotions: string[];
  tags: string[];
  imageText: string;
  /** In the model's words: what it changed and why. */
  reasoning: string;
}

export interface OpenReport {
  id: string;
  templateId: string;
  username: string;
  reason: ReportReason;
  comment: string;
  createdAt: string;
}

export type ReviewStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export interface ReportReview {
  status: ReviewStatus;
  suggestion: ReportSuggestion | null;
  error: string | null;
}

/** One reported meme: the complaints, how it is described now, and the model's new proposal. */
export interface ReportCase {
  templateId: string;
  name: string;
  status: TemplateStatus;
  imageUrl: string;
  current: MemeProfile;
  reports: OpenReport[];
  review: ReportReview | null;
  /** How much the reporters count for together (newcomers 1, proven reporters more, repeat false reporters 0). */
  weight: number;
  /** What they need to count for before the model is asked to look on its own. */
  neededWeight: number;
}

/** Something the rules decided on their own in the last week. */
export interface AutomaticEntry {
  templateId: string;
  name: string;
  imageUrl: string;
  /** APPLIED: the model's proposal was adopted. DISMISSED: nothing was changed. */
  action: 'APPLIED' | 'DISMISSED';
  reports: number;
  note: string | null;
  at: string;
  canUndo: boolean;
}
