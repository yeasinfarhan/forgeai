import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";

import { AuthContext, type AuthContextValue, type AuthStatus } from "@/auth/auth-context";
import { clearSession, loadSession, saveSession } from "@/auth/session-storage";
import {
  ApiError,
  fetchCurrentUser,
  login as loginRequest,
  register as registerRequest,
  setAccessToken,
  setUnauthorizedHandler,
  type AuthResponse,
  type RegisterInput,
  type User,
} from "@/lib/api";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [initialSession] = useState(loadSession);
  const [status, setStatus] = useState<AuthStatus>(initialSession ? "loading" : "anonymous");
  const [user, setUser] = useState<User | null>(initialSession?.user ?? null);
  const [expiresAt, setExpiresAt] = useState<string | null>(initialSession?.expiresAt ?? null);

  const logout = useCallback(() => {
    clearSession();
    setAccessToken(null);
    setUser(null);
    setExpiresAt(null);
    setStatus("anonymous");
  }, []);

  const startSession = useCallback((response: AuthResponse) => {
    saveSession({ accessToken: response.accessToken, expiresAt: response.expiresAt, user: response.user });
    setAccessToken(response.accessToken);
    setUser(response.user);
    setExpiresAt(response.expiresAt);
    setStatus("authenticated");
  }, []);

  // Any 401 on an authenticated request means the token is no longer valid.
  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  // Confirm a saved session with the server on startup.
  useEffect(() => {
    if (!initialSession) return;
    setAccessToken(initialSession.accessToken);

    const controller = new AbortController();
    fetchCurrentUser(controller.signal)
      .then((currentUser) => {
        setUser(currentUser);
        setStatus("authenticated");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 401) {
          logout();
        } else {
          // Server unreachable: keep the saved session; requests will retry when it is back.
          setStatus("authenticated");
        }
      });
    return () => controller.abort();
  }, [initialSession, logout]);

  // Sign out automatically when the token expires.
  useEffect(() => {
    if (!expiresAt) return;
    const remainingMs = Date.parse(expiresAt) - Date.now();
    const timer = window.setTimeout(logout, Math.max(remainingMs, 0));
    return () => window.clearTimeout(timer);
  }, [expiresAt, logout]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      login: async (email, password) => startSession(await loginRequest(email, password)),
      register: async (input: RegisterInput) => startSession(await registerRequest(input)),
      logout,
    }),
    [status, user, startSession, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
