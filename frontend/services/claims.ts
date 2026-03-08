import { apiFetch } from "./api";
import type { Claim, CreateClaimRequest } from "@/types/claims";

type ClaimListResponse = Claim[] | { claims: Claim[] } | { data: Claim[] };
type ClaimDetailResponse = Claim | { claim: Claim } | { data: Claim };

function getAuthHeaders(token: string) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
}

function unwrapClaimList(payload: ClaimListResponse): Claim[] {
  if (Array.isArray(payload)) {
    return payload;
  }

  if ("claims" in payload && Array.isArray(payload.claims)) {
    return payload.claims;
  }

  if ("data" in payload && Array.isArray(payload.data)) {
    return payload.data;
  }

  throw new Error("Unexpected claim list response.");
}

function unwrapClaim(payload: ClaimDetailResponse): Claim {
  if ("id" in payload) {
    return payload;
  }

  if ("claim" in payload && payload.claim && "id" in payload.claim) {
    return payload.claim;
  }

  if ("data" in payload && payload.data && "id" in payload.data) {
    return payload.data;
  }

  throw new Error("Unexpected claim response.");
}

export async function createClaim(
  data: CreateClaimRequest,
  token: string
): Promise<Claim> {
  const response = (await apiFetch("/api/claims", {
    method: "POST",
    headers: getAuthHeaders(token),
    body: JSON.stringify(data),
  })) as ClaimDetailResponse;

  return unwrapClaim(response);
}

export async function listClaims(token: string): Promise<Claim[]> {
  const response = (await apiFetch("/api/claims", {
    method: "GET",
    headers: getAuthHeaders(token),
  })) as ClaimListResponse;

  return unwrapClaimList(response);
}

export async function getClaimById(id: string, token: string): Promise<Claim> {
  const response = (await apiFetch(`/api/claims/${id}`, {
    method: "GET",
    headers: getAuthHeaders(token),
  })) as ClaimDetailResponse;

  return unwrapClaim(response);
}
