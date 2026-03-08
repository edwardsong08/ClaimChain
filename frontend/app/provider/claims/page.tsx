"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useAuthSession } from "@/hooks/use-auth-session";
import { listClaims } from "@/services/claims";
import type { Claim } from "@/types/claims";

const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

function formatStatus(status: string | null | undefined) {
  if (!status) return "UNKNOWN";
  return status.replaceAll("_", " ");
}

function formatAmount(claim: Claim) {
  const amount = claim.currentAmount ?? claim.amount;
  if (typeof amount !== "number") {
    return "N/A";
  }
  return usdFormatter.format(amount);
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) {
    return fallback;
  }

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

export default function ProviderClaimsPage() {
  const { token, isReady, isAuthenticated } = useAuthSession();

  const claimsQuery = useQuery({
    queryKey: ["provider-claims", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in to view claims.");
      }
      return listClaims(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-4xl space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">My Claims</h1>
          <p className="text-sm text-gray-600">
            Review your submitted claims and open details.
          </p>
        </header>

        <div>
          <Link
            href="/provider/claims/new"
            className="inline-flex rounded-md bg-black px-4 py-2 text-sm font-medium text-white"
          >
            Submit New Claim
          </Link>
        </div>

        {!isReady ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Loading session...
          </div>
        ) : !isAuthenticated || !token ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            You must be logged in to view claims.
          </div>
        ) : claimsQuery.isPending ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Loading claims...
          </div>
        ) : claimsQuery.isError ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {getErrorMessage(claimsQuery.error, "Unable to load claims.")}
          </div>
        ) : claimsQuery.data.length === 0 ? (
          <div className="rounded-lg border p-6 text-sm text-gray-600">
            No claims found yet. Use the Submit New Claim action to create your first
            claim.
          </div>
        ) : (
          <ul className="space-y-3">
            {claimsQuery.data.map((claim) => (
              <li key={claim.id} className="rounded-lg border p-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="space-y-1">
                    <p className="text-sm text-gray-600">Claim ID: {claim.id}</p>
                    <p className="font-medium">
                      {claim.debtorName && claim.debtorName.trim().length > 0
                        ? claim.debtorName
                        : "Unnamed Debtor"}
                    </p>
                    <p className="text-sm">Amount: {formatAmount(claim)}</p>
                    <p className="text-sm">Status: {formatStatus(claim.status)}</p>
                  </div>
                  <Link
                    href={`/provider/claims/${claim.id}`}
                    className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
                  >
                    View Details
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
