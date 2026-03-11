"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useAuthSession } from "@/hooks/use-auth-session";
import { isApprovalGateForbiddenError } from "@/lib/api-error-utils";
import { checkoutBuyerPackage, getBuyerPackageDetail } from "@/services/buyer";
import type { BuyerCheckoutResponse, BuyerPackageClaimSummary } from "@/types/buyer";

const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

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

function formatPercent(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  const normalizedPercent = value <= 1 ? value * 100 : value;
  const roundedPercent = Math.round(normalizedPercent * 10) / 10;
  return `${roundedPercent}%`;
}

function textValue(value: string | null | undefined) {
  if (!value || value.trim().length === 0) return "N/A";
  return value;
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) return fallback;

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

function formatStatus(value: string | null | undefined) {
  if (!value) return "N/A";
  return value.replaceAll("_", " ");
}

function hasText(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function normalizeStatus(value: string | null | undefined) {
  if (!hasText(value)) return "LISTED";
  return value.trim().toUpperCase();
}

function resolveCheckoutUrl(response: BuyerCheckoutResponse) {
  const candidates = [
    response.checkoutUrl,
    response.redirectUrl,
    response.sessionUrl,
    response.url,
  ];
  const resolved = candidates.find((candidate) => hasText(candidate));
  return resolved ?? null;
}

function claimKey(claim: BuyerPackageClaimSummary, index: number) {
  if (typeof claim.claimId === "number") return String(claim.claimId);
  return `${index}-${claim.claimType ?? "claim"}`;
}

export default function BuyerPackageDetailPage() {
  const router = useRouter();
  const params = useParams();
  const { token, isReady, isAuthenticated } = useAuthSession();

  const idParam = params?.id;
  const packageId = Array.isArray(idParam) ? idParam[0] : idParam;

  const packageDetailQuery = useQuery({
    queryKey: ["buyer-package-detail", packageId, token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as buyer.");
      }
      if (!packageId) {
        throw new Error("Missing package id.");
      }
      return getBuyerPackageDetail(packageId, token);
    },
    enabled: isReady && isAuthenticated && Boolean(token) && Boolean(packageId),
  });
  const checkoutMutation = useMutation({
    mutationFn: () => {
      if (!token) {
        throw new Error("You must be logged in as buyer.");
      }
      if (!packageId) {
        throw new Error("Missing package id.");
      }
      return checkoutBuyerPackage(packageId, token);
    },
    onSuccess: (response) => {
      const checkoutUrl = resolveCheckoutUrl(response);
      if (!checkoutUrl) {
        throw new Error("Checkout redirect URL was not returned.");
      }
      window.location.href = checkoutUrl;
    },
  });

  const shouldRedirectForApproval =
    packageDetailQuery.isError &&
    isApprovalGateForbiddenError(packageDetailQuery.error);

  useEffect(() => {
    if (shouldRedirectForApproval) {
      router.replace("/buyer/pending-approval");
    }
  }, [router, shouldRedirectForApproval]);

  if (!isReady) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Loading session...</p>
      </main>
    );
  }

  if (!isAuthenticated || !token) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-red-600">
          You must be logged in as buyer to view this package.
        </p>
      </main>
    );
  }

  if (!packageId) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Package not found.</p>
      </main>
    );
  }

  if (packageDetailQuery.isPending || shouldRedirectForApproval) {
    return <main className="min-h-screen" aria-busy="true" />;
  }

  if (packageDetailQuery.isError) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-red-600">
          {getErrorMessage(packageDetailQuery.error, "Package detail unavailable.")}
        </p>
      </main>
    );
  }

  if (!packageDetailQuery.data) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Package not found.</p>
      </main>
    );
  }

  const normalizedPackageStatus = normalizeStatus(packageDetailQuery.data.status);
  const hasPrice = typeof packageDetailQuery.data.price === "number";
  const isPurchasable = hasPrice && normalizedPackageStatus === "LISTED";
  const purchaseUnavailableMessage = !hasPrice
    ? "Package is not yet priced."
    : normalizedPackageStatus !== "LISTED"
      ? "Package is not currently available for purchase."
      : null;
  const purchaseErrorMessage = checkoutMutation.isError
    ? getErrorMessage(checkoutMutation.error, "Unable to start checkout.")
    : null;

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-5xl space-y-6">
        <header className="space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h1 className="text-3xl font-semibold">Package Detail</h1>
            <Link
              href="/buyer/dashboard"
              className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
            >
              Back to Marketplace
            </Link>
          </div>
          <p className="text-sm text-gray-600">Package ID: {packageId ?? "N/A"}</p>
        </header>

        <>
          <section className="space-y-3 rounded-lg border p-5">
            <h2 className="text-xl font-semibold">Summary</h2>
            <div className="grid gap-2 text-sm">
              <p>Package ID: {packageDetailQuery.data.id}</p>
              <p>Status: {formatStatus(packageDetailQuery.data.status ?? "LISTED")}</p>
              <p>Total Claims: {packageDetailQuery.data.totalClaims ?? 0}</p>
              <p>
                Total Face Value: {formatCurrency(packageDetailQuery.data.totalFaceValue)}
              </p>
              <p>Price: {formatCurrency(packageDetailQuery.data.price)}</p>
              <p>Created: {formatDate(packageDetailQuery.data.createdAt)}</p>
            </div>
          </section>

          <section className="space-y-3 rounded-lg border p-5">
            <h2 className="text-xl font-semibold">Included Claim Summaries</h2>
            {Array.isArray(packageDetailQuery.data.claims) &&
            packageDetailQuery.data.claims.length > 0 ? (
              <ul className="space-y-2">
                {packageDetailQuery.data.claims.map((claim, index) => (
                  <li key={claimKey(claim, index)} className="rounded-md border p-3 text-sm">
                    <p>Claim ID: {typeof claim.claimId === "number" ? claim.claimId : "N/A"}</p>
                    <p>Score: {typeof claim.scoreTotal === "number" ? claim.scoreTotal : "N/A"}</p>
                    <p>Grade: {textValue(claim.grade)}</p>
                    <p>
                      Extraction Success: {formatPercent(claim.extractionSuccessRate)}
                    </p>
                    <p>Jurisdiction: {textValue(claim.jurisdictionState)}</p>
                    <p>Debtor Type: {textValue(claim.debtorType)}</p>
                    <p>Claim Type: {textValue(claim.claimType)}</p>
                    <p>Dispute Status: {formatStatus(claim.disputeStatus)}</p>
                    <p>Debt Age (days): {typeof claim.debtAgeDays === "number" ? claim.debtAgeDays : "N/A"}</p>
                    <p>Amount Band: {textValue(claim.amountBand)}</p>
                    <p>Document Types: {textValue(claim.docTypesPresent)}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-gray-600">
                No anonymized claim summaries are available for this package yet.
              </p>
            )}
          </section>

          <section className="rounded-lg border p-5">
            <h2 className="text-xl font-semibold">Purchase</h2>
            <p className="mt-2 text-sm text-gray-600">
              You will be redirected to Stripe checkout to complete payment.
            </p>
            {isPurchasable ? (
              <button
                type="button"
                onClick={() => checkoutMutation.mutate()}
                disabled={checkoutMutation.isPending}
                className="mt-3 rounded-md bg-black px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              >
                {checkoutMutation.isPending ? "Redirecting..." : "Purchase Package"}
              </button>
            ) : (
              <p className="mt-3 text-sm text-gray-600">{purchaseUnavailableMessage}</p>
            )}
            {purchaseErrorMessage && (
              <p className="mt-2 text-sm text-red-600">{purchaseErrorMessage}</p>
            )}
          </section>
        </>
      </div>
    </main>
  );
}
