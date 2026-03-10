import { apiFetch } from "./api";
import type {
  AdminClaim,
  AdminClaimDecisionRequest,
  AdminPackage,
  AdminPackageBuildResponse,
  AdminPackageDetail,
  AdminClaimStatus,
  AdminUser,
  AdminSubmittedClaim,
} from "@/types/admin";
import type { Claim } from "@/types/claims";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL;

function getAuthHeaders(token: string) {
  return {
    Authorization: `Bearer ${token}`,
  };
}

function getJsonAuthHeaders(token: string) {
  return {
    "Content-Type": "application/json",
    ...getAuthHeaders(token),
  };
}

export function listUnverifiedUsers(token: string) {
  return apiFetch("/api/admin/unverified-users", {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<AdminUser[]>;
}

export function listAllUsers(token: string) {
  return apiFetch("/api/admin/users", {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<AdminUser[]>;
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

export function listApprovedClaims(token: string) {
  return listClaimsByStatus(token, "APPROVED");
}

export function listRejectedClaims(token: string) {
  return listClaimsByStatus(token, "REJECTED");
}

export function listAdminPackages(token: string) {
  return apiFetch("/api/admin/packages", {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<AdminPackage[]>;
}

export function getAdminPackageDetail(token: string, packageId: number | string) {
  return apiFetch(`/api/admin/packages/${packageId}`, {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<AdminPackageDetail>;
}

export function previewPackageBuild(token: string, notes?: string) {
  const payload = {
    dryRun: true,
    notes,
  };

  return apiFetch("/api/admin/packages/build", {
    method: "POST",
    headers: getJsonAuthHeaders(token),
    body: JSON.stringify(payload),
  }) as Promise<AdminPackageBuildResponse>;
}

export function createPackage(token: string, notes?: string) {
  const payload = {
    dryRun: false,
    notes,
  };

  return apiFetch("/api/admin/packages/build", {
    method: "POST",
    headers: getJsonAuthHeaders(token),
    body: JSON.stringify(payload),
  }) as Promise<AdminPackageBuildResponse>;
}

export async function listPackage(packageId: number | string, token: string) {
  const response = await fetch(`${API_BASE}/api/admin/packages/${packageId}/list`, {
    method: "POST",
    headers: getAuthHeaders(token),
  });

  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(responseText || "Unable to list package.");
  }
}

export async function unlistPackage(packageId: number | string, token: string) {
  const response = await fetch(`${API_BASE}/api/admin/packages/${packageId}/unlist`, {
    method: "POST",
    headers: getAuthHeaders(token),
  });

  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(responseText || "Unable to unlist package.");
  }
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

export function returnClaimToReview(claimId: number | string, token: string) {
  return apiFetch(`/api/admin/claims/${claimId}/return-to-review`, {
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
    headers: getJsonAuthHeaders(token),
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

export async function deleteClaim(claimId: number | string, token: string) {
  const response = await fetch(`${API_BASE}/api/admin/claims/${claimId}`, {
    method: "DELETE",
    headers: getAuthHeaders(token),
  });

  const responseText = await response.text();

  if (!response.ok) {
    throw new Error(responseText || "Unable to delete claim.");
  }

  return responseText;
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

export async function rejectUser(token: string, userId: number, reason: string) {
  const response = await fetch(`${API_BASE}/api/admin/reject-user/${userId}`, {
    method: "POST",
    headers: getJsonAuthHeaders(token),
    body: JSON.stringify({ reason }),
  });

  const responseText = await response.text();

  if (!response.ok) {
    throw new Error(responseText || "User rejection failed.");
  }

  return responseText;
}
