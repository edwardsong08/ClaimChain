export type BuyerPackageSummary = {
  id: number;
  status?: string | null;
  totalClaims?: number | null;
  totalFaceValue?: number | null;
  price?: number | null;
  createdAt?: string | null;
  purchasedAt?: string | null;
};

export type BuyerPackageClaimSummary = {
  claimId?: number | null;
  jurisdictionState?: string | null;
  debtorType?: string | null;
  claimType?: string | null;
  disputeStatus?: string | null;
  debtAgeDays?: number | null;
  amountBand?: string | null;
  scoreTotal?: number | null;
  grade?: string | null;
  extractionSuccessRate?: number | null;
  docTypesPresent?: string | null;
};

export type BuyerPackageDetail = {
  id: number;
  status?: string | null;
  totalClaims?: number | null;
  totalFaceValue?: number | null;
  price?: number | null;
  createdAt?: string | null;
  purchasedAt?: string | null;
  claims?: BuyerPackageClaimSummary[] | null;
};

export type BuyerCheckoutResponse = {
  purchaseId?: number | null;
  checkoutSessionId?: string | null;
  checkoutUrl?: string | null;
  redirectUrl?: string | null;
  sessionUrl?: string | null;
  url?: string | null;
};
