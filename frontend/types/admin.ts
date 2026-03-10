export type AdminUserVerificationStatus = "PENDING" | "APPROVED" | "REJECTED";

export type AdminUser = {
  id: number;
  name?: string | null;
  email?: string | null;
  role?: string | null;
  verificationStatus?: AdminUserVerificationStatus | string | null;
  phone?: string | null;
  address?: string | null;
  einOrLicense?: string | null;
  businessType?: string | null;
  businessName?: string | null;
  verifiedAt?: string | null;
  verifiedByUserId?: number | null;
  verifiedByEmail?: string | null;
  rejectedAt?: string | null;
  rejectReason?: string | null;
};

export type AdminPendingUser = AdminUser;

export type AdminClaim = {
  id: number;
  debtorName?: string | null;
  clientName?: string | null;
  currentAmount?: number | null;
  amount?: number | null;
  status?: string | null;
};

export type AdminClaimStatus =
  | "SUBMITTED"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED";

export type AdminClaimDecision = "APPROVE" | "REJECT";

export type AdminClaimDecisionRequest = {
  decision: AdminClaimDecision;
  notes: string;
  missingDocs: string[];
};

export type AdminSubmittedClaim = AdminClaim;

export type AdminPackageStatus = "DRAFT" | "READY" | "LISTED" | "SOLD";

export type AdminPackage = {
  id: number;
  status?: string | null;
  totalClaims?: number | null;
  totalFaceValue?: number | null;
  priceCents?: number | null;
  currency?: string | null;
  rulesetId?: number | null;
  rulesetVersion?: number | null;
  createdAt?: string | null;
};

export type AdminPackageBuildRequest = {
  notes?: string;
  dryRun?: boolean;
};

export type AdminPackageBuildResponse = {
  packageId?: number | null;
  dryRun: boolean;
  buildable: boolean;
  status?: string | null;
  rulesetId?: number | null;
  rulesetVersion?: number | null;
  totalClaims?: number | null;
  totalFaceValue?: number | null;
  claimIds?: number[] | null;
  failureReasons?: string[] | null;
};

export type AdminPackageDetail = {
  id: number;
  status?: string | null;
  totalClaims?: number | null;
  totalFaceValue?: number | null;
  priceCents?: number | null;
  currency?: string | null;
  notes?: string | null;
  createdAt?: string | null;
  createdByUserId?: number | null;
  rulesetId?: number | null;
  rulesetVersion?: number | null;
  claimIds?: number[] | null;
};
