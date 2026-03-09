export type BuyerPackageSummary = {
  id: number;
  totalClaims?: number | null;
  totalFaceValue?: number | null;
  createdAt?: string | null;
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
  totalClaims?: number | null;
  totalFaceValue?: number | null;
  createdAt?: string | null;
  claims?: BuyerPackageClaimSummary[] | null;
};
