import { apiFetch } from "./api";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types/auth";

export function login(data: LoginRequest) {
  return apiFetch("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(data),
  }) as Promise<AuthResponse>;
}

export function register(data: RegisterRequest) {
  return apiFetch("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(data),
  }) as Promise<AuthResponse>;
}