import { apiFetch } from "./api";
import type { AuthResponse, LoginRequest, RegisterRequest, UserRole } from "@/types/auth";

type LoginApiResponse = {
  token?: string;
  accessToken?: string;
  role?: string;
  refreshToken?: string;
};

type RegisterApiResponse = {
  token?: string;
  accessToken?: string;
  role?: string;
};

function normalizeRole(rawRole: string | null | undefined): UserRole | null {
  if (!rawRole) return null;

  const normalizedRole = rawRole.trim().toUpperCase().replace(/^ROLE_/, "");

  switch (normalizedRole) {
    case "PROVIDER":
    case "SERVICE_PROVIDER":
      return "SERVICE_PROVIDER";
    case "BUYER":
    case "COLLECTION_AGENCY":
      return "COLLECTION_AGENCY";
    case "ADMIN":
      return "ADMIN";
    default:
      return null;
  }
}

function extractRoleFromJwt(token: string): UserRole | null {
  const tokenParts = token.split(".");
  if (tokenParts.length < 2) return null;
  if (typeof window === "undefined") return null;

  try {
    const base64 = tokenParts[1].replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = window.atob(base64);

    const payload = JSON.parse(jsonPayload) as { role?: string };
    return normalizeRole(payload.role);
  } catch {
    return null;
  }
}

function resolveToken(response: { token?: string; accessToken?: string }) {
  return response.token ?? response.accessToken ?? null;
}

export async function login(data: LoginRequest): Promise<AuthResponse> {
  const response = (await apiFetch("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(data),
  })) as LoginApiResponse;

  const token = resolveToken(response);
  if (!token) {
    throw new Error("Login response is missing access token.");
  }

  const role = normalizeRole(response.role) ?? extractRoleFromJwt(token);
  if (!role) {
    throw new Error("Login response is missing role.");
  }

  return {
    token,
    role,
  };
}

export async function register(data: RegisterRequest): Promise<AuthResponse> {
  const response = (await apiFetch("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(data),
  })) as RegisterApiResponse;

  const token = resolveToken(response);
  if (!token) {
    throw new Error("Register response is missing access token.");
  }

  const role = normalizeRole(response.role) ?? normalizeRole(data.role);
  if (!role) {
    throw new Error("Register response is missing role.");
  }

  return {
    token,
    role,
  };
}
