import type {
  AutomaticEntry,
  CollectionRun,
  CollectionSource,
  GenerationView,
  HotSearch,
  IngestResult,
  IndexSyncResult,
  LibraryItem,
  LibraryStats,
  LoginResponse,
  MemeProfile,
  MemeSummary,
  SearchResult,
  ReportCase,
  ReportReason,
  Slot,
  TemplateStatus,
  TemplateSummary,
  TemplateView,
} from './types';

/** A failed request. `status` is 0 when the server could not be reached at all. */
export class ApiError extends Error {
  readonly status: number;
  /** The server's own explanation (English); shown as a secondary line. */
  readonly detail: string;
  readonly retryAfterSeconds: number | null;

  constructor(status: number, detail: string, retryAfterSeconds: number | null = null) {
    super(detail || `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.detail = detail;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

interface AuthHooks {
  getToken: () => string | null;
  /** Called when the server rejects the token of a signed-in user. */
  onUnauthorized: () => void;
}

let auth: AuthHooks = { getToken: () => null, onUnauthorized: () => undefined };

export function configureAuth(hooks: AuthHooks): void {
  auth = hooks;
}

interface RequestOptions {
  json?: unknown;
  form?: FormData;
  /** False for calls made before signing in, where a 401 means "wrong password", not "session over". */
  authenticated?: boolean;
}

async function send(method: string, path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  const authenticated = options.authenticated !== false;
  const token = authenticated ? auth.getToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  let body: BodyInit | undefined;
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.json);
  } else if (options.form) {
    body = options.form; // the browser sets the multipart boundary itself
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body });
  } catch {
    throw new ApiError(0, 'Could not reach the server');
  }

  if (!response.ok) {
    if (response.status === 401 && authenticated && token) auth.onUnauthorized();
    throw await toApiError(response);
  }
  return response;
}

async function toApiError(response: Response): Promise<ApiError> {
  let detail = '';
  try {
    const problem: unknown = await response.json();
    if (typeof problem === 'object' && problem !== null) {
      const p = problem as { detail?: unknown; title?: unknown };
      detail = typeof p.detail === 'string' ? p.detail : typeof p.title === 'string' ? p.title : '';
    }
  } catch {
    // The body was not JSON; the status code alone will have to do.
  }
  const retryAfter = Number(response.headers.get('Retry-After'));
  return new ApiError(response.status, detail, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null);
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(method, path, options);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const enc = encodeURIComponent;

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('POST', '/api/auth/login', { json: { username, password }, authenticated: false }),

  register: (username: string, password: string) =>
    request<{ id: string; username: string }>('POST', '/api/auth/register', {
      json: { username, password },
      authenticated: false,
    }),

  search: (query: string, limit = 10) =>
    request<SearchResult[]>('GET', `/api/templates/search?q=${enc(query)}&limit=${limit}`),

  submitGeneration: (situation: string) =>
    request<{ jobId: string }>('POST', '/api/generations', { json: { situation } }),

  getGeneration: (jobId: string) => request<GenerationView>('GET', `/api/generations/${enc(jobId)}`),

  keepMeme: (memeId: string) => request<void>('POST', `/api/memes/${enc(memeId)}/keep`),

  listMemes: (status?: 'KEPT' | 'COMPOSED', limit = 50) =>
    request<MemeSummary[]>('GET', `/api/memes?limit=${limit}${status ? `&status=${status}` : ''}`),

  /** The finished image as a file, fetched through the application so the caller's permission is checked. */
  async downloadMeme(memeId: string): Promise<Blob> {
    const response = await send('GET', `/api/memes/${enc(memeId)}/image`, {});
    return response.blob();
  },

  library: {
    /** Memes picked at random from the published library. */
    random: (limit = 12) => request<LibraryItem[]>('GET', `/api/library/random?limit=${limit}`),

    hotSearches: (limit = 8) => request<HotSearch[]>('GET', `/api/library/hot-searches?limit=${limit}`),

    /** The picture as a file, fetched through the application so a canvas may draw it and a download works. */
    async image(templateId: string): Promise<Blob> {
      const response = await send('GET', `/api/library/${enc(templateId)}/image`, {});
      return response.blob();
    },
  },

  reports: {
    /** Tells the administrators a meme's description or tags do not fit it. */
    submit: (templateId: string, reason: ReportReason, comment: string) =>
      request<void>('POST', '/api/reports', { json: { templateId, reason, comment } }),
  },

  favorites: {
    list: () => request<LibraryItem[]>('GET', '/api/favorites'),

    add: (templateId: string) => request<void>('PUT', `/api/favorites/${enc(templateId)}`),

    remove: (templateId: string) => request<void>('DELETE', `/api/favorites/${enc(templateId)}`),
  },

  admin: {
    listTemplates: (status?: TemplateStatus) =>
      request<TemplateSummary[]>('GET', `/api/admin/templates${status ? `?status=${status}` : ''}`),

    getTemplate: (id: string) => request<TemplateView>('GET', `/api/admin/templates/${enc(id)}`),

    draftTemplate(name: string, file: File) {
      const form = new FormData();
      form.append('name', name);
      form.append('file', file);
      return request<{ id: string }>('POST', '/api/admin/templates', { form });
    },

    saveProfile: (id: string, profile: MemeProfile) =>
      request<void>('PUT', `/api/admin/templates/${enc(id)}/profile`, { json: profile }),

    defineSlot: (id: string, slot: Slot) =>
      request<void>('POST', `/api/admin/templates/${enc(id)}/slots`, { json: slot }),

    redefineSlot: (id: string, slot: Slot) =>
      request<void>('PUT', `/api/admin/templates/${enc(id)}/slots/${slot.slotNo}`, { json: slot }),

    removeSlot: (id: string, slotNo: number) =>
      request<void>('DELETE', `/api/admin/templates/${enc(id)}/slots/${slotNo}`),

    approve: (id: string) => request<void>('POST', `/api/admin/templates/${enc(id)}/approve`),

    retire: (id: string) => request<void>('POST', `/api/admin/templates/${enc(id)}/retire`),

    syncIndex: () => request<IndexSyncResult>('POST', '/api/admin/index/sync'),

    reports: {
      list: () => request<ReportCase[]>('GET', '/api/admin/reports'),

      /** What the rules closed or adopted on their own in the last week. */
      automatic: () => request<AutomaticEntry[]>('GET', '/api/admin/reports/automatic'),

      /** Takes back an automatic decision: the earlier description, or the closed reports. */
      undo: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/undo`),

      /** Adopts the model's proposal as the meme's description and closes its reports. */
      apply: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/apply`),

      dismiss: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/dismiss`),

      /** Has the vision model look at the meme again. */
      reanalyze: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/reanalyze`),
    },

    collection: {
      stats: () => request<LibraryStats>('GET', '/api/admin/collection/status'),

      addFiles(files: File[]) {
        const form = new FormData();
        for (const file of files) form.append('files', file);
        return request<IngestResult[]>('POST', '/api/admin/collection/files', { form });
      },

      addUrl: (url: string, title?: string) =>
        request<IngestResult>('POST', '/api/admin/collection/url', { json: { url, title: title || null } }),

      sources: () => request<CollectionSource[]>('GET', '/api/admin/collection/sources'),

      startRun: (source: string, limit: number, options: Record<string, string>) =>
        request<{ runId: string }>('POST', '/api/admin/collection/runs', { json: { source, limit, options } }),

      runs: () => request<CollectionRun[]>('GET', '/api/admin/collection/runs'),
    },
  },
};
