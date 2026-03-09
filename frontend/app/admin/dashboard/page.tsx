"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuthSession } from "@/hooks/use-auth-session";
import {
  deleteClaim,
  listApprovedClaims,
  listInReviewClaims,
  listRejectedClaims,
  listSubmittedClaims,
  listUnverifiedUsers,
  returnClaimToReview,
  startReviewClaim,
  verifyUser,
} from "@/services/admin";
import type { AdminClaim } from "@/types/admin";

const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

function formatStatus(status: string | null | undefined) {
  if (!status) return "UNKNOWN";
  return status.replaceAll("_", " ");
}

function formatAmount(claim: AdminClaim) {
  const amount = claim.currentAmount ?? claim.amount;
  if (typeof amount !== "number") return "N/A";
  return usdFormatter.format(amount);
}

function textValue(value: string | null | undefined, fallback = "N/A") {
  if (!value || value.trim().length === 0) return fallback;
  return value;
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) return fallback;

  try {
    const parsed = JSON.parse(error.message) as {
      message?: string;
      error?: string;
    };

    if (typeof parsed.message === "string" && parsed.message.length > 0) {
      return parsed.message;
    }

    if (typeof parsed.error === "string" && parsed.error.length > 0) {
      return parsed.error;
    }
  } catch {
    return error.message || fallback;
  }

  return error.message || fallback;
}

export default function AdminDashboardPage() {
  const { token, isReady, isAuthenticated } = useAuthSession();
  const queryClient = useQueryClient();

  const [verifyingUserId, setVerifyingUserId] = useState<number | null>(null);
  const [reviewingClaimId, setReviewingClaimId] = useState<number | null>(null);
  const [returningClaimId, setReturningClaimId] = useState<number | null>(null);
  const [deletingClaimId, setDeletingClaimId] = useState<number | null>(null);

  const pendingUsersQuery = useQuery({
    queryKey: ["admin-unverified-users", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listUnverifiedUsers(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  const submittedClaimsQuery = useQuery({
    queryKey: ["admin-submitted-claims", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listSubmittedClaims(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  const inReviewClaimsQuery = useQuery({
    queryKey: ["admin-in-review-claims", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listInReviewClaims(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  const approvedClaimsQuery = useQuery({
    queryKey: ["admin-approved-claims", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listApprovedClaims(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  const rejectedClaimsQuery = useQuery({
    queryKey: ["admin-rejected-claims", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listRejectedClaims(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  const verifyUserMutation = useMutation({
    mutationFn: (userId: number) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return verifyUser(token, userId);
    },
    onSuccess: async () => {
      toast.success("User verified successfully.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-unverified-users", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "User verification failed."));
    },
  });

  const startReviewMutation = useMutation({
    mutationFn: (claimId: number) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return startReviewClaim(token, claimId);
    },
    onSuccess: async () => {
      toast.success("Claim moved to review.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-submitted-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-in-review-claims", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to start review."));
    },
  });

  const returnToReviewMutation = useMutation({
    mutationFn: (claimId: number) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return returnClaimToReview(claimId, token);
    },
    onSuccess: async () => {
      toast.success("Claim returned to review.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-in-review-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-approved-claims", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-rejected-claims", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to return claim to review."));
    },
  });

  const deleteClaimMutation = useMutation({
    mutationFn: (claimId: number) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return deleteClaim(claimId, token);
    },
    onSuccess: async () => {
      toast.success("Claim deleted successfully.");
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
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to delete claim."));
    },
  });

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-5xl space-y-8">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Admin Dashboard</h1>
          <p className="text-sm text-gray-600">
            Verify new users and process submitted claims.
          </p>
        </header>

        {!isReady ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Loading session...
          </div>
        ) : !isAuthenticated || !token ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            You must be logged in as admin to view this page.
          </div>
        ) : (
          <>
            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Package Management</h2>
              <p className="text-sm text-gray-600">
                Build, preview, list, and unlist claim packages.
              </p>
              <Link
                href="/admin/packages"
                className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
              >
                Manage Packages
              </Link>
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Pending User Verification</h2>

              {pendingUsersQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading pending users...</p>
              ) : pendingUsersQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    pendingUsersQuery.error,
                    "Unable to load pending users."
                  )}
                </p>
              ) : pendingUsersQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">
                  No users are waiting for verification.
                </p>
              ) : (
                <ul className="space-y-2">
                  {pendingUsersQuery.data.map((user) => (
                    <li key={user.id} className="rounded-md border p-3">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="space-y-1 text-sm">
                          <p>User ID: {user.id}</p>
                          <p>Email: {textValue(user.email)}</p>
                          <p>Role: {textValue(user.role)}</p>
                          <p>
                            Verification: {textValue(user.verificationStatus, "PENDING")}
                          </p>
                        </div>
                        <button
                          type="button"
                          disabled={
                            verifyUserMutation.isPending && verifyingUserId === user.id
                          }
                          onClick={() => {
                            setVerifyingUserId(user.id);
                            verifyUserMutation.mutate(user.id, {
                              onSettled: () => setVerifyingUserId(null),
                            });
                          }}
                          className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                        >
                          {verifyUserMutation.isPending && verifyingUserId === user.id
                            ? "Verifying..."
                            : "Verify"}
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Submitted Claims Review Queue</h2>

              {submittedClaimsQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading submitted claims...</p>
              ) : submittedClaimsQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    submittedClaimsQuery.error,
                    "Unable to load submitted claims."
                  )}
                </p>
              ) : submittedClaimsQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">
                  No claims are currently waiting for review.
                </p>
              ) : (
                <ul className="space-y-2">
                  {submittedClaimsQuery.data.map((claim) => (
                    <li key={claim.id} className="rounded-md border p-3">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="space-y-1 text-sm">
                          <p>Claim ID: {claim.id}</p>
                          <p>Debtor: {textValue(claim.debtorName, "Unnamed Debtor")}</p>
                          <p>Client: {textValue(claim.clientName)}</p>
                          <p>Amount: {formatAmount(claim)}</p>
                          <p>Status: {formatStatus(claim.status)}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            disabled={
                              startReviewMutation.isPending &&
                              reviewingClaimId === claim.id
                            }
                            onClick={() => {
                              setReviewingClaimId(claim.id);
                              startReviewMutation.mutate(claim.id, {
                                onSettled: () => setReviewingClaimId(null),
                              });
                            }}
                            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                          >
                            {startReviewMutation.isPending &&
                            reviewingClaimId === claim.id
                              ? "Starting..."
                              : "Start Review"}
                          </button>
                          <button
                            type="button"
                            disabled={
                              deleteClaimMutation.isPending &&
                              deletingClaimId === claim.id
                            }
                            onClick={() => {
                              const confirmed = window.confirm(
                                "Are you sure you want to delete this claim? This action cannot be undone."
                              );
                              if (!confirmed) return;
                              setDeletingClaimId(claim.id);
                              deleteClaimMutation.mutate(claim.id, {
                                onSettled: () => setDeletingClaimId(null),
                              });
                            }}
                            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                          >
                            {deleteClaimMutation.isPending && deletingClaimId === claim.id
                              ? "Deleting..."
                              : "Delete Claim"}
                          </button>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">In Review Claims</h2>

              {inReviewClaimsQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading in-review claims...</p>
              ) : inReviewClaimsQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    inReviewClaimsQuery.error,
                    "Unable to load in-review claims."
                  )}
                </p>
              ) : inReviewClaimsQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">
                  No claims are currently in review.
                </p>
              ) : (
                <ul className="space-y-2">
                  {inReviewClaimsQuery.data.map((claim) => (
                    <li key={claim.id} className="rounded-md border p-3">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="space-y-1 text-sm">
                          <p>Claim ID: {claim.id}</p>
                          <p>Debtor: {textValue(claim.debtorName, "Unnamed Debtor")}</p>
                          <p>Client: {textValue(claim.clientName)}</p>
                          <p>Amount: {formatAmount(claim)}</p>
                          <p>Status: {formatStatus(claim.status)}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Link
                            href={`/admin/claims/${claim.id}`}
                            className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
                          >
                            Open Review
                          </Link>
                          <button
                            type="button"
                            disabled={
                              deleteClaimMutation.isPending &&
                              deletingClaimId === claim.id
                            }
                            onClick={() => {
                              const confirmed = window.confirm(
                                "Are you sure you want to delete this claim? This action cannot be undone."
                              );
                              if (!confirmed) return;
                              setDeletingClaimId(claim.id);
                              deleteClaimMutation.mutate(claim.id, {
                                onSettled: () => setDeletingClaimId(null),
                              });
                            }}
                            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                          >
                            {deleteClaimMutation.isPending && deletingClaimId === claim.id
                              ? "Deleting..."
                              : "Delete Claim"}
                          </button>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Approved Claims</h2>

              {approvedClaimsQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading approved claims...</p>
              ) : approvedClaimsQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    approvedClaimsQuery.error,
                    "Unable to load approved claims."
                  )}
                </p>
              ) : approvedClaimsQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">
                  No claims have been approved yet.
                </p>
              ) : (
                <ul className="space-y-2">
                  {approvedClaimsQuery.data.map((claim) => (
                    <li key={claim.id} className="rounded-md border p-3">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="space-y-1 text-sm">
                          <p>Claim ID: {claim.id}</p>
                          <p>Debtor: {textValue(claim.debtorName, "Unnamed Debtor")}</p>
                          <p>Client: {textValue(claim.clientName)}</p>
                          <p>Amount: {formatAmount(claim)}</p>
                          <p>Status: {formatStatus(claim.status)}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Link
                            href={`/admin/claims/${claim.id}`}
                            className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
                          >
                            View Claim
                          </Link>
                          <button
                            type="button"
                            disabled={
                              returnToReviewMutation.isPending &&
                              returningClaimId === claim.id
                            }
                            onClick={() => {
                              setReturningClaimId(claim.id);
                              returnToReviewMutation.mutate(claim.id, {
                                onSettled: () => setReturningClaimId(null),
                              });
                            }}
                            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                          >
                            {returnToReviewMutation.isPending &&
                            returningClaimId === claim.id
                              ? "Returning..."
                              : "Return to Review"}
                          </button>
                          <button
                            type="button"
                            disabled={
                              deleteClaimMutation.isPending &&
                              deletingClaimId === claim.id
                            }
                            onClick={() => {
                              const confirmed = window.confirm(
                                "Are you sure you want to delete this claim? This action cannot be undone."
                              );
                              if (!confirmed) return;
                              setDeletingClaimId(claim.id);
                              deleteClaimMutation.mutate(claim.id, {
                                onSettled: () => setDeletingClaimId(null),
                              });
                            }}
                            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                          >
                            {deleteClaimMutation.isPending && deletingClaimId === claim.id
                              ? "Deleting..."
                              : "Delete Claim"}
                          </button>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Rejected Claims</h2>

              {rejectedClaimsQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading rejected claims...</p>
              ) : rejectedClaimsQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    rejectedClaimsQuery.error,
                    "Unable to load rejected claims."
                  )}
                </p>
              ) : rejectedClaimsQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">
                  No claims have been rejected.
                </p>
              ) : (
                <ul className="space-y-2">
                  {rejectedClaimsQuery.data.map((claim) => (
                    <li key={claim.id} className="rounded-md border p-3">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="space-y-1 text-sm">
                          <p>Claim ID: {claim.id}</p>
                          <p>Debtor: {textValue(claim.debtorName, "Unnamed Debtor")}</p>
                          <p>Client: {textValue(claim.clientName)}</p>
                          <p>Amount: {formatAmount(claim)}</p>
                          <p>Status: {formatStatus(claim.status)}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Link
                            href={`/admin/claims/${claim.id}`}
                            className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
                          >
                            View Claim
                          </Link>
                          <button
                            type="button"
                            disabled={
                              returnToReviewMutation.isPending &&
                              returningClaimId === claim.id
                            }
                            onClick={() => {
                              setReturningClaimId(claim.id);
                              returnToReviewMutation.mutate(claim.id, {
                                onSettled: () => setReturningClaimId(null),
                              });
                            }}
                            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                          >
                            {returnToReviewMutation.isPending &&
                            returningClaimId === claim.id
                              ? "Returning..."
                              : "Return to Review"}
                          </button>
                          <button
                            type="button"
                            disabled={
                              deleteClaimMutation.isPending &&
                              deletingClaimId === claim.id
                            }
                            onClick={() => {
                              const confirmed = window.confirm(
                                "Are you sure you want to delete this claim? This action cannot be undone."
                              );
                              if (!confirmed) return;
                              setDeletingClaimId(claim.id);
                              deleteClaimMutation.mutate(claim.id, {
                                onSettled: () => setDeletingClaimId(null),
                              });
                            }}
                            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                          >
                            {deleteClaimMutation.isPending && deletingClaimId === claim.id
                              ? "Deleting..."
                              : "Delete Claim"}
                          </button>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </>
        )}
      </div>
    </main>
  );
}
