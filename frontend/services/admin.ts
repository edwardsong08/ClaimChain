import { apiFetch } from "./api";
import type {
  AdminClaim,
  AdminClaimDecisionRequest,
  AdminClaimStatus,
  AdminPendingUser,
  AdminSubmittedClaim,
} from "@/types/admin";
import type { Claim } from "@/types/claims";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL;

function getAuthHeaders(token: string) {
  return {
    Authorization: `Bearer ${token}`,
  };
}

export function listUnverifiedUsers(token: string) {
  return apiFetch("/api/admin/unverified-users", {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<AdminPendingUser[]>;
}

export function listClaimsByStatus(token: string, status: AdminClaimStatus) {
  return apiFetch(`/api/admin/claims?status=${encodeURIComponent(status)}`, {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<AdminClaim[]>;
}

export function listSubmittedClaims(token: string) {
  return listClaimsByStatus(token, "SUBMITTED") as Promise<AdminSubmittedClaim[]>;
}

export function listInReviewClaims(token: string) {
  return listClaimsByStatus(token, "UNDER_REVIEW");
}

export function getAdminClaimById(token: string, claimId: string) {
  return apiFetch(`/api/claims/${claimId}`, {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<Claim>;
}

export function startReviewClaim(token: string, claimId: number) {
  return apiFetch(`/api/admin/claims/${claimId}/start-review`, {
    method: "POST",
    headers: getAuthHeaders(token),
  }) as Promise<AdminClaim>;
}

export function decideClaim(
  token: string,
  claimId: number,
  payload: AdminClaimDecisionRequest
) {
  return apiFetch(`/api/admin/claims/${claimId}/decision`, {
    method: "POST",
    headers: getAuthHeaders(token),
    body: JSON.stringify(payload),
  }) as Promise<AdminClaim>;
}

export async function rescoreClaim(token: string, claimId: number) {
  const response = await fetch(`${API_BASE}/api/admin/claims/${claimId}/rescore`, {
    method: "POST",
    headers: getAuthHeaders(token),
  });

  if (!response.ok) {
    const responseText = await response.text();
    throw new Error(responseText || "Unable to rescore claim.");
  }
}

export async function verifyUser(token: string, userId: number) {
  const response = await fetch(`${API_BASE}/api/admin/verify-user/${userId}`, {
    method: "POST",
    headers: getAuthHeaders(token),
  });

  const responseText = await response.text();

  if (!response.ok) {
    throw new Error(responseText || "User verification failed.");
  }

  return responseText;
}
