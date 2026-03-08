"use client";

import { useSyncExternalStore } from "react";
import {
  AUTH_SESSION_CHANGED_EVENT,
  clearAuthSession,
  getAuthRole,
  getAuthToken,
} from "@/lib/auth-storage";

type AuthSession = {
  token: string | null;
  role: string | null;
  isAuthenticated: boolean;
  isReady: boolean;
};

const SERVER_SNAPSHOT: AuthSession = {
  token: null,
  role: null,
  isAuthenticated: false,
  isReady: false,
};

let clientSnapshot: AuthSession = {
  token: null,
  role: null,
  isAuthenticated: false,
  isReady: true,
};

function readAuthSessionSnapshot(): AuthSession {
  const token = getAuthToken();
  const role = getAuthRole();

  if (token === clientSnapshot.token && role === clientSnapshot.role) {
    return clientSnapshot;
  }

  clientSnapshot = {
    token,
    role,
    isAuthenticated: Boolean(token),
    isReady: true,
  };

  return clientSnapshot;
}

function subscribeToAuthSession(onStoreChange: () => void) {
  if (typeof window === "undefined") {
    return () => {};
  }

  const handleChange = () => onStoreChange();

  window.addEventListener(AUTH_SESSION_CHANGED_EVENT, handleChange);
  window.addEventListener("storage", handleChange);

  return () => {
    window.removeEventListener(AUTH_SESSION_CHANGED_EVENT, handleChange);
    window.removeEventListener("storage", handleChange);
  };
}

export function useAuthSession() {
  const session = useSyncExternalStore(
    subscribeToAuthSession,
    readAuthSessionSnapshot,
    () => SERVER_SNAPSHOT
  );

  const logout = () => {
    clearAuthSession();
  };

  return {
    ...session,
    logout,
  };
}
