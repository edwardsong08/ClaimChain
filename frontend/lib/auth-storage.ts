const TOKEN_KEY = "claimchain_token";
const ROLE_KEY = "claimchain_role";

export function setAuthSession(token: string, role: string) {
  if (typeof window === "undefined") return;
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ROLE_KEY, role);
}

export function getAuthToken() {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getAuthRole() {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ROLE_KEY);
}

export function clearAuthSession() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
}