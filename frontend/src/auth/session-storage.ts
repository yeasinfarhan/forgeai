import type { User } from "@/lib/api";

const STORAGE_KEY = "forgeai.session";

export interface StoredSession {
  accessToken: string;
  expiresAt: string;
  user: User;
}

/** Reads the saved session, ignoring it if it is malformed or already expired. */
export function loadSession(): StoredSession | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as StoredSession;
    if (!session.accessToken || Date.parse(session.expiresAt) <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function saveSession(session: StoredSession) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Storage unavailable (e.g. private mode); the session lasts until the tab closes.
  }
}

export function clearSession() {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing to clear.
  }
}
