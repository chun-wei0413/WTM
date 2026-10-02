import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { api, configureAuth } from '../api/client';
import { decodeToken, isExpired, type TokenInfo } from '../lib/jwt';

const STORAGE_KEY = 'usethatmeme.token';

export interface CurrentUser {
  id: string;
  username: string;
  isAdmin: boolean;
}

interface AuthContextValue {
  user: CurrentUser | null;
  login: (username: string, password: string) => Promise<void>;
  /** Creates the account, then signs in with it. */
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

interface Session {
  token: string;
  info: TokenInfo;
}

/**
 * The token lives in sessionStorage: it survives a reload of the tab but is gone when the tab closes,
 * and it is never written anywhere that outlives the browsing session.
 */
function loadSession(): Session | null {
  try {
    const token = sessionStorage.getItem(STORAGE_KEY);
    if (!token) return null;
    const info = decodeToken(token);
    return info && !isExpired(info) ? { token, info } : null;
  } catch {
    return null;
  }
}

function saveSession(token: string | null): void {
  try {
    if (token) sessionStorage.setItem(STORAGE_KEY, token);
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage can be unavailable (private mode); the session then lasts until the page is reloaded.
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(loadSession);
  const tokenRef = useRef<string | null>(session?.token ?? null);

  const logout = useCallback(() => {
    tokenRef.current = null;
    saveSession(null);
    setSession(null);
  }, []);

  const startSession = useCallback((token: string) => {
    const info = decodeToken(token);
    if (!info) throw new Error('The server sent a token that cannot be read');
    tokenRef.current = token;
    saveSession(token);
    setSession({ token, info });
  }, []);

  // A layout effect, not a plain one: React runs a child's effects before its parent's, so with a plain
  // effect the page's first request (made on load, e.g. after reloading the tab) would go out before the
  // token was ready and be refused. All layout effects run before any plain effect in the tree.
  useLayoutEffect(() => {
    configureAuth({ getToken: () => tokenRef.current, onUnauthorized: logout });
  }, [logout]);

  // Sign out by itself the moment the token runs out, instead of waiting for a request to fail.
  useEffect(() => {
    if (!session) return;
    const remaining = session.info.expiresAt - Date.now();
    const timer = setTimeout(logout, Math.max(remaining, 0));
    return () => clearTimeout(timer);
  }, [session, logout]);

  const login = useCallback(
    async (username: string, password: string) => {
      const { token } = await api.login(username, password);
      startSession(token);
    },
    [startSession],
  );

  const register = useCallback(
    async (username: string, password: string) => {
      await api.register(username, password);
      await login(username, password);
    },
    [login],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session
        ? { id: session.info.userId, username: session.info.username, isAdmin: session.info.roles.includes('ADMIN') }
        : null,
      login,
      register,
      logout,
    }),
    [session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
