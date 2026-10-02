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

export interface SearchResult {
  templateId: string;
  name: string;
  score: number;
  imageUrl: string;
  slots: Slot[];
}

export interface IndexSyncResult {
  indexed: number;
  removed: number;
  failed: number;
}
