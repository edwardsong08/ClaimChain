import { apiFetch } from "./api";
import type {
  ClaimDocument,
  UploadClaimDocumentRequest,
  UploadClaimDocumentResponse,
} from "@/types/documents";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL;
const INLINE_VIEWABLE_EXTENSIONS = new Set([
  "pdf",
  "png",
  "jpg",
  "jpeg",
  "gif",
  "bmp",
  "webp",
  "svg",
  "txt",
]);

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

function parseFilenameFromContentDisposition(value: string | null): string | null {
  if (!value) {
    return null;
  }

  const utf8Match = value.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }

  const simpleMatch = value.match(/filename="?([^";]+)"?/i);
  if (simpleMatch && simpleMatch[1]) {
    return simpleMatch[1];
  }

  return null;
}

function extractFileExtension(filename: string | null | undefined): string | null {
  if (!filename) {
    return null;
  }

  const lastDotIndex = filename.lastIndexOf(".");
  if (lastDotIndex < 0 || lastDotIndex === filename.length - 1) {
    return null;
  }

  return filename.slice(lastDotIndex + 1).toLowerCase();
}

export function isInlineViewableDocument(
  contentType: string | null | undefined,
  filename: string | null | undefined
) {
  const normalizedType = (contentType || "").split(";")[0].trim().toLowerCase();
  if (
    normalizedType === "application/pdf" ||
    normalizedType === "text/plain" ||
    normalizedType.startsWith("image/")
  ) {
    return true;
  }

  const extension = extractFileExtension(filename);
  return extension ? INLINE_VIEWABLE_EXTENSIONS.has(extension) : false;
}

export async function downloadClaimDocument(token: string, documentId: number) {
  const response = await fetch(`${API_BASE}/api/documents/${documentId}/download`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Unable to access document.");
  }

  const contentDisposition = response.headers.get("content-disposition");
  const filename = parseFilenameFromContentDisposition(contentDisposition);
  const contentType = response.headers.get("content-type") || "application/octet-stream";
  const blob = await response.blob();

  return {
    blob: blob.type ? blob : new Blob([blob], { type: contentType }),
    filename,
    contentType,
  };
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
