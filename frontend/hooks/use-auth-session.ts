"use client";

import { useEffect, useState } from "react";
import {
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

export function useAuthSession() {
  const [session, setSession] = useState<AuthSession>({
    token: null,
    role: null,
    isAuthenticated: false,
    isReady: false,
  });

  useEffect(() => {
    const token = getAuthToken();
    const role = getAuthRole();

    setSession({
      token,
      role,
      isAuthenticated: Boolean(token),
      isReady: true,
    });
  }, []);

  const logout = () => {
    clearAuthSession();
    setSession({
      token: null,
      role: null,
      isAuthenticated: false,
      isReady: true,
    });
  };

  return {
    ...session,
    logout,
  };
}