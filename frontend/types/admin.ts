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
