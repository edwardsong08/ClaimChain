"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuthSession } from "@/hooks/use-auth-session";
import {
  deleteClaim,
  decideClaim,
  getAdminClaimById,
  rescoreClaim,
  returnClaimToReview,
} from "@/services/admin";
import { listClaimDocuments } from "@/services/documents";
import type { AdminClaimDecision } from "@/types/admin";
import type { Claim } from "@/types/claims";

const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

function formatStatus(status: string | null | undefined) {
  if (!status) return "N/A";
  return status.replaceAll("_", " ");
}

function formatCurrency(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  return usdFormatter.format(value);
}

function formatDate(value: string | null | undefined) {
  if (!value) return "N/A";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString();
}

function textValue(value: string | null | undefined) {
  if (!value || value.trim().length === 0) return "N/A";
  return value;
}

function formatBytes(value: number | null | undefined) {
  if (typeof value !== "number" || value < 0) return "N/A";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function formatPercent(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  const normalizedPercent = value <= 1 ? value * 100 : value;
  const roundedPercent = Math.round(normalizedPercent * 10) / 10;
  return `${roundedPercent}%`;
}

function extractStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];

  return value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

function parseExplainabilityFactors(explainabilityJson: string | null | undefined) {
  if (!explainabilityJson) return [];

  try {
    const parsed = JSON.parse(explainabilityJson) as {
      contributions?: Array<{
        reason?: string;
        delta?: number;
        ruleId?: string;
      }>;
      eligibleReasons?: string[];
    };

    const contributionItems = Array.isArray(parsed.contributions)
      ? parsed.contributions
          .map((item) => {
            const reason =
              typeof item.reason === "string" && item.reason.trim().length > 0
                ? item.reason.trim()
                : typeof item.ruleId === "string" && item.ruleId.trim().length > 0
                  ? item.ruleId.trim()
                  : null;

            if (!reason) return null;

            if (typeof item.delta === "number") {
              const sign = item.delta >= 0 ? "+" : "";
              return `${reason} (${sign}${item.delta})`;
            }
            return reason;
          })
          .filter((item): item is string => Boolean(item))
      : [];

    const eligibilityItems = extractStringArray(parsed.eligibleReasons);

    return [...contributionItems, ...eligibilityItems];
  } catch {
    return [];
  }
}

function getScoreBreakdownItems(claim: Claim) {
  const rawItems = [
    ...extractStringArray(claim.scoreBreakdown),
    ...extractStringArray(claim.scoringFactors),
    ...parseExplainabilityFactors(claim.explainabilityJson),
  ];

  const subscoreItems = [
    {
      label: "Enforceability",
      value: claim.subscoreEnforceability,
    },
    {
      label: "Documentation completeness",
      value: claim.subscoreDocumentation,
    },
    {
      label: "Collectability",
      value: claim.subscoreCollectability,
    },
    {
      label: "Operational risk",
      value: claim.subscoreOperationalRisk,
    },
  ]
    .filter((item) => typeof item.value === "number")
    .map((item) => `${item.label}: ${item.value}`);

  return Array.from(new Set([...rawItems, ...subscoreItems]));
}

function hasScoreData(claim: Claim) {
  return (
    typeof claim.scoreTotal === "number" ||
    (typeof claim.grade === "string" && claim.grade.trim().length > 0) ||
    typeof claim.extractionSuccessRate === "number"
  );
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) {
    return fallback;
  }

  try {
    const parsed = JSON.parse(error.message) as {
      message?: string;
      error?: string;
      details?: string[];
    };

    if (typeof parsed.message === "string" && parsed.message.length > 0) {
      return parsed.message;
    }

    if (typeof parsed.error === "string" && parsed.error.length > 0) {
      return parsed.error;
    }

    if (Array.isArray(parsed.details) && parsed.details.length > 0) {
      return parsed.details[0];
    }
  } catch {
    return error.message || fallback;
  }

  return error.message || fallback;
}

function parseMissingDocs(value: string) {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

export default function AdminClaimDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { token, isReady, isAuthenticated } = useAuthSession();

  const idParam = params?.id;
  const claimId = Array.isArray(idParam) ? idParam[0] : idParam;

  const [notes, setNotes] = useState("");
  const [missingDocsInput, setMissingDocsInput] = useState("");
  const [decisionInFlight, setDecisionInFlight] =
    useState<AdminClaimDecision | null>(null);

  const numericClaimId = claimId ? Number(claimId) : Number.NaN;

  const claimQuery = useQuery({
    queryKey: ["admin-claim-detail", claimId, token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!claimId) {
        throw new Error("Missing claim id.");
      }
      return getAdminClaimById(token, claimId);
    },
    enabled: isReady && isAuthenticated && Boolean(token) && Boolean(claimId),
  });

  const documentsQuery = useQuery({
    queryKey: ["admin-claim-documents", claimId, token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!claimId) {
        throw new Error("Missing claim id.");
      }
      return listClaimDocuments(token, claimId);
    },
    enabled: isReady && isAuthenticated && Boolean(token) && Boolean(claimId),
  });

  const decisionMutation = useMutation({
    mutationFn: async (decision: AdminClaimDecision) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!Number.isFinite(numericClaimId)) {
        throw new Error("Invalid claim id.");
      }

      const parsedMissingDocs = parseMissingDocs(missingDocsInput);
      const normalizedNotes = notes.trim();
      return decideClaim(token, numericClaimId, {
        decision,
        notes: normalizedNotes,
        missingDocs: parsedMissingDocs,
      });
    },
    onSuccess: async (_, decision) => {
      toast.success(
        decision === "APPROVE"
          ? "Claim approved successfully."
          : "Claim rejected successfully."
      );
      await queryClient.invalidateQueries({
        queryKey: ["admin-submitted-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-in-review-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-approved-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-rejected-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-claim-detail", claimId, token],
      });
      router.push("/admin/dashboard");
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to submit decision."));
    },
  });

  const rescoreMutation = useMutation({
    mutationFn: async () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!Number.isFinite(numericClaimId)) {
        throw new Error("Invalid claim id.");
      }
      await rescoreClaim(token, numericClaimId);
    },
    onSuccess: async () => {
      toast.success("Claim rescored successfully.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-in-review-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-claim-detail", claimId, token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to rescore claim."));
    },
  });

  const returnToReviewMutation = useMutation({
    mutationFn: async () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!Number.isFinite(numericClaimId)) {
        throw new Error("Invalid claim id.");
      }
      return returnClaimToReview(numericClaimId, token);
    },
    onSuccess: async () => {
      toast.success("Claim returned to review.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-claim-detail", claimId, token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-in-review-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-approved-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-rejected-claims", token],
      });
      router.push("/admin/dashboard");
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to return claim to review."));
    },
  });

  const deleteClaimMutation = useMutation({
    mutationFn: async () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!Number.isFinite(numericClaimId)) {
        throw new Error("Invalid claim id.");
      }
      return deleteClaim(numericClaimId, token);
    },
    onSuccess: async () => {
      toast.success("Claim deleted successfully.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-claim-detail", claimId, token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-submitted-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-in-review-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-approved-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-rejected-claims", token],
      });
      router.push("/admin/dashboard");
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to delete claim."));
    },
  });

  const claim = claimQuery.data;
  const canSubmitDecision = claim?.status === "UNDER_REVIEW";
  const canReturnToReview =
    claim?.status === "APPROVED" || claim?.status === "REJECTED";
  const isActionPending =
    decisionMutation.isPending ||
    rescoreMutation.isPending ||
    returnToReviewMutation.isPending ||
    deleteClaimMutation.isPending;

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-4xl space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Admin Claim Review</h1>
          <p className="text-sm text-gray-600">Claim ID: {claimId ?? "N/A"}</p>
        </header>

        {!isReady ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Loading session...
          </div>
        ) : !isAuthenticated || !token ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            You must be logged in as admin to view this claim.
          </div>
        ) : !claimId ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Claim not found.
          </div>
        ) : claimQuery.isPending ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Loading claim details...
          </div>
        ) : claimQuery.isError ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {getErrorMessage(claimQuery.error, "Unable to load claim details.")}
          </div>
        ) : !claim ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Claim not found.
          </div>
        ) : (
          <section className="space-y-3">
            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Summary</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>Claim Type: {textValue(claim.claimType)}</p>
                <p>Debt Type: {textValue(claim.debtType)}</p>
                <p>Jurisdiction: {textValue(claim.jurisdictionState)}</p>
                <p>Date of Default: {formatDate(claim.dateOfDefault)}</p>
                <p>Date of Service: {formatDate(claim.dateOfService)}</p>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Status</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>Status: {formatStatus(claim.status)}</p>
                <p>Dispute Status: {textValue(claim.disputeStatus)}</p>
                <p>Submitted At: {formatDate(claim.submittedAt)}</p>
                <p>Submitted By: {textValue(claim.submittedBy)}</p>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Financial Details</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>Current Amount: {formatCurrency(claim.currentAmount)}</p>
                <p>Original Amount: {formatCurrency(claim.originalAmount)}</p>
                <p>Amount: {formatCurrency(claim.amount)}</p>
                <p>Last Payment Date: {formatDate(claim.lastPaymentDate)}</p>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Client / Debtor Details</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>Client Name: {textValue(claim.clientName)}</p>
                <p>Client Contact: {textValue(claim.clientContact)}</p>
                <p>Client Address: {textValue(claim.clientAddress)}</p>
                <p>Debtor Name: {textValue(claim.debtorName)}</p>
                <p>Debtor Email: {textValue(claim.debtorEmail)}</p>
                <p>Debtor Phone: {textValue(claim.debtorPhone)}</p>
                <p>Debtor Address: {textValue(claim.debtorAddress)}</p>
                <p>Debtor Type: {textValue(claim.debtorType)}</p>
                <p>Contact History: {textValue(claim.contactHistory)}</p>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Documents</h2>

              {documentsQuery.isPending ? (
                <p className="mt-2 text-sm text-gray-600">Loading documents...</p>
              ) : documentsQuery.isError ? (
                <p className="mt-2 text-sm text-red-600">
                  {getErrorMessage(documentsQuery.error, "Unable to load documents.")}
                </p>
              ) : !documentsQuery.data || documentsQuery.data.length === 0 ? (
                <p className="mt-2 text-sm text-gray-600">
                  No documents uploaded yet.
                </p>
              ) : (
                <ul className="mt-2 space-y-2">
                  {documentsQuery.data.map((document) => (
                    <li key={document.id} className="rounded-md border p-3 text-sm">
                      <p className="font-medium">{textValue(document.filename)}</p>
                      <p>Type: {formatStatus(document.documentType)}</p>
                      <p>Status: {formatStatus(document.status)}</p>
                      <p>Extraction: {formatStatus(document.extractionStatus)}</p>
                      <p>Size: {formatBytes(document.sizeBytes)}</p>
                      <p>Uploaded: {formatDate(document.createdAt)}</p>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {claim.status === "APPROVED" && (
              <div className="rounded-lg border p-4">
                <h2 className="text-lg font-semibold">Score / Explainability</h2>
                {hasScoreData(claim) ? (
                  <>
                    <div className="mt-2 grid gap-2 text-sm">
                      <p>Score: {typeof claim.scoreTotal === "number" ? claim.scoreTotal : "N/A"}</p>
                      <p>Grade: {textValue(claim.grade)}</p>
                      <p>Extraction Success: {formatPercent(claim.extractionSuccessRate)}</p>
                      <p>Scored At: {formatDate(claim.scoredAt)}</p>
                      <p>Score Trigger: {textValue(claim.scoreTrigger)}</p>
                    </div>

                    {getScoreBreakdownItems(claim).length > 0 && (
                      <div className="mt-4">
                        <h3 className="text-sm font-semibold">Score Breakdown</h3>
                        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm">
                          {getScoreBreakdownItems(claim).map((item) => (
                            <li key={item}>{item}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </>
                ) : (
                  <p className="mt-2 text-sm text-gray-600">
                    Scoring pending. Scores are generated after claim review.
                  </p>
                )}
              </div>
            )}

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Review Actions</h2>
              <p className="mt-1 text-sm text-gray-600">
                Use optional notes and missing docs to support review decisions.
              </p>
              {!canSubmitDecision && (
                <p className="mt-2 text-sm text-amber-700">
                  Claim must be in UNDER_REVIEW status before approve or reject.
                </p>
              )}
              <div className="mt-3 space-y-3">
                <div className="space-y-1">
                  <label htmlFor="decision-notes" className="block text-sm font-medium">
                    Notes (optional)
                  </label>
                  <textarea
                    id="decision-notes"
                    value={notes}
                    onChange={(event) => setNotes(event.target.value)}
                    rows={3}
                    className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-black"
                    placeholder="Add context for this review decision."
                  />
                </div>

                <div className="space-y-1">
                  <label htmlFor="missing-docs" className="block text-sm font-medium">
                    Missing Docs (optional, comma-separated)
                  </label>
                  <input
                    id="missing-docs"
                    type="text"
                    value={missingDocsInput}
                    onChange={(event) => setMissingDocsInput(event.target.value)}
                    className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-black"
                    placeholder="invoice, statement, authorization"
                  />
                </div>

                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={isActionPending || !canSubmitDecision}
                    onClick={() => {
                      if (isActionPending || !canSubmitDecision) return;
                      setDecisionInFlight("APPROVE");
                      decisionMutation.mutate("APPROVE", {
                        onSettled: () => setDecisionInFlight(null),
                      });
                    }}
                    className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                  >
                    {decisionMutation.isPending && decisionInFlight === "APPROVE"
                      ? "Approving..."
                      : "Approve"}
                  </button>

                  <button
                    type="button"
                    disabled={isActionPending || !canSubmitDecision}
                    onClick={() => {
                      if (isActionPending || !canSubmitDecision) return;
                      setDecisionInFlight("REJECT");
                      decisionMutation.mutate("REJECT", {
                        onSettled: () => setDecisionInFlight(null),
                      });
                    }}
                    className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                  >
                    {decisionMutation.isPending && decisionInFlight === "REJECT"
                      ? "Rejecting..."
                      : "Reject"}
                  </button>

                  <button
                    type="button"
                    disabled={isActionPending}
                    onClick={() => {
                      if (isActionPending) return;
                      rescoreMutation.mutate();
                    }}
                    className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                  >
                    {rescoreMutation.isPending ? "Rescoring..." : "Rescore"}
                  </button>

                  {canReturnToReview && (
                    <button
                      type="button"
                      disabled={isActionPending}
                      onClick={() => {
                        if (isActionPending) return;
                        returnToReviewMutation.mutate();
                      }}
                      className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                    >
                      {returnToReviewMutation.isPending
                        ? "Returning..."
                        : "Return to Review"}
                    </button>
                  )}

                  <button
                    type="button"
                    disabled={isActionPending}
                    onClick={() => {
                      if (isActionPending) return;
                      const confirmed = window.confirm(
                        "Are you sure you want to delete this claim? This action cannot be undone."
                      );
                      if (!confirmed) return;
                      deleteClaimMutation.mutate();
                    }}
                    className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                  >
                    {deleteClaimMutation.isPending ? "Deleting..." : "Delete Claim"}
                  </button>
                </div>
              </div>
            </div>
          </section>
        )}

        <Link href="/admin/dashboard" className="inline-flex text-sm underline">
          Back to Admin Dashboard
        </Link>
      </div>
    </main>
  );
}
