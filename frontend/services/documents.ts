import { apiFetch } from "./api";
import type {
  ClaimDocument,
  UploadClaimDocumentRequest,
  UploadClaimDocumentResponse,
} from "@/types/documents";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL;

function parseJsonSafely<T>(text: string): T | null {
  if (!text) return null;
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

export function listClaimDocuments(token: string, claimId: string) {
  return apiFetch(`/api/claims/${claimId}/documents`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  }) as Promise<ClaimDocument[]>;
}

export async function uploadClaimDocument(
  token: string,
  payload: UploadClaimDocumentRequest
): Promise<UploadClaimDocumentResponse> {
  const formData = new FormData();
  formData.append("file", payload.file);
  formData.append("documentType", payload.documentType);

  const response = await fetch(`${API_BASE}/api/claims/${payload.claimId}/documents`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: formData,
  });

  const text = await response.text();

  if (!response.ok) {
    throw new Error(text || "Document upload failed.");
  }

  const parsed = parseJsonSafely<UploadClaimDocumentResponse>(text);
  if (!parsed) {
    throw new Error("Document uploaded, but response was not valid JSON.");
  }

  return parsed;
}
