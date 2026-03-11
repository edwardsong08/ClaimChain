import { apiFetch } from "./api";
import type {
  BuyerCheckoutResponse,
  BuyerPackageDetail,
  BuyerPackageSummary,
} from "@/types/buyer";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL;

function getAuthHeaders(token: string) {
  return {
    Authorization: `Bearer ${token}`,
  };
}

export function listBuyerPackages(token: string) {
  return apiFetch("/api/buyer/packages", {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<BuyerPackageSummary[]>;
}

export function getBuyerPackageDetail(packageId: string | number, token: string) {
  return apiFetch(`/api/buyer/packages/${packageId}`, {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<BuyerPackageDetail>;
}

export function listBuyerPurchasedPackages(token: string) {
  return apiFetch("/api/buyer/purchases", {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<BuyerPackageSummary[]>;
}

export function getBuyerPurchasedPackageDetail(packageId: string | number, token: string) {
  return apiFetch(`/api/buyer/purchases/${packageId}`, {
    method: "GET",
    headers: getAuthHeaders(token),
  }) as Promise<BuyerPackageDetail>;
}

export function checkoutBuyerPackage(packageId: string | number, token: string) {
  return apiFetch(`/api/buyer/packages/${packageId}/checkout`, {
    method: "POST",
    headers: getAuthHeaders(token),
  }) as Promise<BuyerCheckoutResponse>;
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

  const simpleMatch = value.match(/filename=\"?([^\";]+)\"?/i);
  if (simpleMatch && simpleMatch[1]) {
    return simpleMatch[1];
  }

  return null;
}

export async function downloadBuyerPurchasedPackageExport(
  packageId: number | string,
  token: string
) {
  const response = await fetch(`${API_BASE}/api/buyer/packages/${packageId}/export`, {
    method: "GET",
    headers: getAuthHeaders(token),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Unable to export package.");
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
