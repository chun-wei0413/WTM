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
}

export type TemplateStatus = 'DRAFT' | 'APPROVED' | 'RETIRED';

export interface TemplateSummary {
  id: string;
  name: string;
  status: TemplateStatus;
  version: number;
  imageUrl: string;
  updatedAt: string;
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

export type GenerationStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/** Caption text by slot number. JSON object keys are always text, so the numbers arrive as "1", "2", … */
export type Captions = Record<string, string>;

export interface Candidate {
  memeId: string;
  templateId: string;
  templateName: string;
  status: 'COMPOSED' | 'KEPT';
  imageUrl: string | null;
  captions: Captions;
}

export interface GenerationView {
  id: string;
  status: GenerationStatus;
  situation: string;
  failureReason: string | null;
  createdAt: string;
  candidates: Candidate[];
}

export interface MemeSummary {
  id: string;
  templateId: string;
  templateName: string;
  status: 'COMPOSED' | 'KEPT';
  imageUrl: string;
  captions: Captions;
  createdAt: string;
}

/** One meme of the library, as the browsing pages show it. */
export interface LibraryItem {
  templateId: string;
  name: string;
  imageUrl: string;
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
