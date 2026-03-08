export type AdminPendingUser = {
  id: number;
  email?: string | null;
  role?: string | null;
  verificationStatus?: string | null;
};

export type AdminClaim = {
  id: number;
  debtorName?: string | null;
  clientName?: string | null;
  currentAmount?: number | null;
  amount?: number | null;
  status?: string | null;
};

export type AdminClaimStatus = "SUBMITTED" | "UNDER_REVIEW";

export type AdminClaimDecision = "APPROVE" | "REJECT";

export type AdminClaimDecisionRequest = {
  decision: AdminClaimDecision;
  notes?: string | null;
  missingDocs?: string[] | null;
};

export type AdminSubmittedClaim = AdminClaim;
