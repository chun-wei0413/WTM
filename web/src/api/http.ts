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

export async function send(method: string, path: string, options: RequestOptions): Promise<Response> {
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

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(method, path, options);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** Makes a value safe to put in a URL path or query. */
export const enc = encodeURIComponent;
