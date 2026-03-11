import { apiFetch } from "./api";
import type {
  BuyerCheckoutResponse,
  BuyerPackageDetail,
  BuyerPackageSummary,
} from "@/types/buyer";

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

export function checkoutBuyerPackage(packageId: string | number, token: string) {
  return apiFetch(`/api/buyer/packages/${packageId}/checkout`, {
    method: "POST",
    headers: getAuthHeaders(token),
  }) as Promise<BuyerCheckoutResponse>;
}
