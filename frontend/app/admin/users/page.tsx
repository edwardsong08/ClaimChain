"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuthSession } from "@/hooks/use-auth-session";
import { listAllUsers, rejectUser, verifyUser } from "@/services/admin";
import type { AdminUser } from "@/types/admin";

type UserStatus = "PENDING" | "APPROVED" | "REJECTED" | "UNKNOWN";

function hasText(value: string | null | undefined): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function textValue(value: string | null | undefined, fallback = "N/A") {
  if (!hasText(value)) return fallback;
  return value.trim();
}

function normalizeStatus(status: string | null | undefined): UserStatus {
  if (!hasText(status)) return "UNKNOWN";
  const value = status.trim().toUpperCase();
  if (value === "PENDING" || value === "APPROVED" || value === "REJECTED") {
    return value;
  }
  return "UNKNOWN";
}

function statusBadgeClass(status: UserStatus) {
  if (status === "PENDING") {
    return "border-yellow-300 bg-yellow-100 text-yellow-800";
  }
  if (status === "APPROVED") {
    return "border-green-300 bg-green-100 text-green-800";
  }
  if (status === "REJECTED") {
    return "border-red-300 bg-red-100 text-red-800";
  }
  return "border-gray-300 bg-gray-100 text-gray-700";
}

function formatTimestamp(value: string | null | undefined) {
  if (!hasText(value)) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) return fallback;

  try {
    const parsed = JSON.parse(error.message) as {
      message?: string;
      error?: string;
      details?: string[];
    };

    if (hasText(parsed.message)) {
      return parsed.message;
    }

    if (hasText(parsed.error)) {
      return parsed.error;
    }

    if (Array.isArray(parsed.details) && parsed.details.length > 0) {
      const firstDetail = parsed.details[0];
      if (hasText(firstDetail)) {
        return firstDetail;
      }
    }
  } catch {
    return error.message || fallback;
  }

  return error.message || fallback;
}

function OptionalDetail({
  label,
  value,
}: {
  label: string;
  value: string | null | undefined;
}) {
  if (!hasText(value)) return null;
  return (
    <p>
      <span className="text-gray-500">{label}:</span> {value}
    </p>
  );
}

function UserCard({
  user,
  isVerifying,
  isRejecting,
  onApprove,
  onReject,
}: {
  user: AdminUser;
  isVerifying: boolean;
  isRejecting: boolean;
  onApprove: () => void;
  onReject: () => void;
}) {
  const status = normalizeStatus(user.verificationStatus);
  const isPending = status === "PENDING";
  const verifiedAt = formatTimestamp(user.verifiedAt);
  const rejectedAt = formatTimestamp(user.rejectedAt);
  const displayName = hasText(user.name)
    ? user.name.trim()
    : hasText(user.businessName)
      ? user.businessName.trim()
      : `User #${user.id}`;

  return (
    <li className="rounded-lg border p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <h2 className="text-lg font-semibold">{displayName}</h2>
          <p className="text-sm text-gray-600">{textValue(user.email)}</p>
        </div>
        <span
          className={`inline-flex w-fit rounded-md border px-2 py-1 text-xs font-semibold ${statusBadgeClass(status)}`}
        >
          {status}
        </span>
      </div>

      <div className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
        <p>
          <span className="text-gray-500">User ID:</span> {user.id}
        </p>
        <p>
          <span className="text-gray-500">Role:</span> {textValue(user.role)}
        </p>
        <OptionalDetail label="Business Name" value={user.businessName} />
        <OptionalDetail label="Business Type" value={user.businessType} />
        <OptionalDetail label="Phone" value={user.phone} />
        <OptionalDetail label="Address" value={user.address} />
        <OptionalDetail label="EIN or License" value={user.einOrLicense} />
        <OptionalDetail label="Verified At" value={verifiedAt} />
        <OptionalDetail label="Verified By" value={user.verifiedByEmail} />
        <OptionalDetail label="Rejected At" value={rejectedAt} />
        <OptionalDetail label="Reject Reason" value={user.rejectReason} />
      </div>

      {isPending && (
        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={onApprove}
            disabled={isVerifying || isRejecting}
            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
          >
            {isVerifying ? "Approving..." : "Approve"}
          </button>
          <button
            type="button"
            onClick={onReject}
            disabled={isVerifying || isRejecting}
            className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
          >
            {isRejecting ? "Rejecting..." : "Reject"}
          </button>
        </div>
      )}
    </li>
  );
}

export default function AdminUsersPage() {
  const { token, isReady, isAuthenticated } = useAuthSession();
  const queryClient = useQueryClient();
  const [verifyingUserId, setVerifyingUserId] = useState<number | null>(null);
  const [rejectingUserId, setRejectingUserId] = useState<number | null>(null);

  const usersQuery = useQuery({
    queryKey: ["admin-users", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listAllUsers(token);
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
      toast.success("User approved successfully.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-users", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-unverified-users", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to approve user."));
    },
  });

  const rejectUserMutation = useMutation({
    mutationFn: ({ userId, reason }: { userId: number; reason: string }) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return rejectUser(token, userId, reason);
    },
    onSuccess: async () => {
      toast.success("User rejected successfully.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-users", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-unverified-users", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to reject user."));
    },
  });

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-6xl space-y-6">
        <header className="space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h1 className="text-3xl font-semibold">User Management</h1>
            <Link
              href="/admin/dashboard"
              className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
            >
              Back to Dashboard
            </Link>
          </div>
          <p className="text-sm text-gray-600">
            Review all users and approve or reject pending accounts.
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
        ) : usersQuery.isPending ? (
          <section className="rounded-lg border p-5">
            <p className="text-sm text-gray-600">Loading users...</p>
          </section>
        ) : usersQuery.isError ? (
          <section className="rounded-lg border p-5">
            <p className="text-sm text-red-600">
              {getErrorMessage(usersQuery.error, "Unable to load users.")}
            </p>
          </section>
        ) : usersQuery.data.length === 0 ? (
          <section className="rounded-lg border p-5">
            <p className="text-sm text-gray-600">No users found.</p>
          </section>
        ) : (
          <section className="space-y-3 rounded-lg border p-5">
            <h2 className="text-xl font-semibold">All Users</h2>
            <ul className="space-y-3">
              {usersQuery.data.map((user) => (
                <UserCard
                  key={user.id}
                  user={user}
                  isVerifying={
                    verifyUserMutation.isPending && verifyingUserId === user.id
                  }
                  isRejecting={
                    rejectUserMutation.isPending && rejectingUserId === user.id
                  }
                  onApprove={() => {
                    setVerifyingUserId(user.id);
                    verifyUserMutation.mutate(user.id, {
                      onSettled: () => setVerifyingUserId(null),
                    });
                  }}
                  onReject={() => {
                    const reason = window.prompt(
                      "Enter a rejection reason for this user."
                    );
                    if (reason === null) return;

                    const normalizedReason = reason.trim();
                    if (normalizedReason.length === 0) {
                      toast.error("Rejection reason is required.");
                      return;
                    }

                    setRejectingUserId(user.id);
                    rejectUserMutation.mutate(
                      { userId: user.id, reason: normalizedReason },
                      {
                        onSettled: () => setRejectingUserId(null),
                      }
                    );
                  }}
                />
              ))}
            </ul>
          </section>
        )}
      </div>
    </main>
  );
}
