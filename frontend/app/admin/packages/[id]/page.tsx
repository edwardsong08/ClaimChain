"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  generatePackageAnonymizedViews,
  getAdminPackageDetail,
  listClaimsByStatus,
  listPackageAnonymizedViews,
  setAdminPackagePrice,
} from "@/services/admin";
import type {
  AdminAnonymizedClaimView,
  AdminClaim,
  AdminClaimStatus,
} from "@/types/admin";
import { useAuthSession } from "@/hooks/use-auth-session";

const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

const claimStatuses: AdminClaimStatus[] = [
  "SUBMITTED",
  "UNDER_REVIEW",
  "APPROVED",
  "REJECTED",
];

type FeedbackState = {
  tone: "success" | "error";
  message: string;
} | null;

function hasText(value: string | null | undefined): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function textValue(value: string | null | undefined, fallback = "N/A") {
  if (!hasText(value)) return fallback;
  return value.trim();
}

function formatStatus(value: string | null | undefined) {
  if (!hasText(value)) return "N/A";
  return value.trim().replaceAll("_", " ");
}

function normalizeStatus(value: string | null | undefined) {
  if (!hasText(value)) return "";
  return value.trim().toUpperCase();
}

function getPricingBlockedMessage(status: string) {
  if (status === "LISTED") {
    return "Unlist package before changing price.";
  }
  if (status === "SOLD") {
    return "Sold packages cannot be repriced.";
  }
  return "Only READY packages can be priced.";
}

function formatCurrency(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  return usdFormatter.format(value);
}

function formatDateTime(value: string | null | undefined) {
  if (!hasText(value)) return null;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
}

function formatPercent(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  const normalizedPercent = value <= 1 ? value * 100 : value;
  const roundedPercent = Math.round(normalizedPercent * 10) / 10;
  return `${roundedPercent}%`;
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

function OptionalMetaRow({
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

function OptionalNumberMetaRow({
  label,
  value,
}: {
  label: string;
  value: number | null | undefined;
}) {
  if (typeof value !== "number") return null;

  return (
    <p>
      <span className="text-gray-500">{label}:</span> {value}
    </p>
  );
}

function ClaimSummaryCard({
  claimId,
  claim,
}: {
  claimId: number;
  claim?: AdminClaim;
}) {
  if (!claim) {
    return (
      <li className="rounded-md border p-3 text-sm">
        <p>Claim ID: {claimId}</p>
        <p className="text-gray-600">Claim details unavailable.</p>
      </li>
    );
  }

  const claimAmount = claim.currentAmount ?? claim.amount;
  const submittedAt = formatDateTime(claim.submittedAt);

  return (
    <li className="rounded-md border p-3 text-sm">
      <div className="grid gap-1 sm:grid-cols-2">
        <p>
          <span className="text-gray-500">Claim ID:</span> {claim.id}
        </p>
        <p>
          <span className="text-gray-500">Status:</span> {formatStatus(claim.status)}
        </p>
        <OptionalMetaRow label="Client Name" value={claim.clientName} />
        <OptionalMetaRow label="Debtor Name" value={claim.debtorName} />
        <OptionalMetaRow label="Debtor Type" value={claim.debtorType} />
        <p>
          <span className="text-gray-500">Amount:</span> {formatCurrency(claimAmount)}
        </p>
        <p>
          <span className="text-gray-500">Score Total:</span>{" "}
          {typeof claim.scoreTotal === "number" ? claim.scoreTotal : "N/A"}
        </p>
        <OptionalMetaRow label="Grade" value={claim.grade} />
        <p>
          <span className="text-gray-500">Extraction Success:</span>{" "}
          {formatPercent(claim.extractionSuccessRate)}
        </p>
        <OptionalMetaRow label="Submitted At" value={submittedAt} />
      </div>
    </li>
  );
}

function AnonymizedViewCard({
  view,
  index,
}: {
  view: AdminAnonymizedClaimView;
  index: number;
}) {
  const claimLabel = typeof view.claimId === "number" ? view.claimId : `#${index + 1}`;

  return (
    <li className="rounded-md border p-3 text-sm">
      <div className="grid gap-1 sm:grid-cols-2">
        <p>
          <span className="text-gray-500">Claim:</span> {claimLabel}
        </p>
        <p>
          <span className="text-gray-500">Score Total:</span>{" "}
          {typeof view.scoreTotal === "number" ? view.scoreTotal : "N/A"}
        </p>
        <OptionalMetaRow label="Grade" value={view.grade} />
        <p>
          <span className="text-gray-500">Extraction Success:</span>{" "}
          {formatPercent(view.extractionSuccessRate)}
        </p>
        <OptionalMetaRow label="Jurisdiction" value={view.jurisdictionState} />
        <OptionalMetaRow label="Debtor Type" value={view.debtorType} />
        <OptionalMetaRow label="Claim Type" value={view.claimType} />
        {hasText(view.disputeStatus) ? (
          <OptionalMetaRow
            label="Dispute Status"
            value={formatStatus(view.disputeStatus)}
          />
        ) : null}
        <p>
          <span className="text-gray-500">Debt Age (days):</span>{" "}
          {typeof view.debtAgeDays === "number" ? view.debtAgeDays : "N/A"}
        </p>
        <OptionalMetaRow label="Amount Band" value={view.amountBand} />
        <OptionalMetaRow label="Document Types" value={view.docTypesPresent} />
      </div>
    </li>
  );
}

export default function AdminPackageDetailPage() {
  const params = useParams();
  const { token, isReady, isAuthenticated } = useAuthSession();
  const queryClient = useQueryClient();

  const [feedback, setFeedback] = useState<FeedbackState>(null);
  const [pricingFeedback, setPricingFeedback] = useState<FeedbackState>(null);
  const [priceInput, setPriceInput] = useState<string | null>(null);

  const idParam = params?.id;
  const packageId = Array.isArray(idParam) ? idParam[0] : idParam;
  const numericPackageId = packageId ? Number(packageId) : Number.NaN;

  const packageDetailQuery = useQuery({
    queryKey: ["admin-package-detail", packageId, token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!packageId) {
        throw new Error("Missing package id.");
      }
      return getAdminPackageDetail(token, packageId);
    },
    enabled: isReady && isAuthenticated && Boolean(token) && Boolean(packageId),
  });

  const claimIds = Array.isArray(packageDetailQuery.data?.claimIds)
    ? packageDetailQuery.data?.claimIds.filter(
        (claimId): claimId is number => typeof claimId === "number"
      )
    : [];

  const claimIndexQuery = useQuery({
    queryKey: ["admin-claims-index-for-packages", token],
    queryFn: async () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      const claimGroups = await Promise.all(
        claimStatuses.map((status) => listClaimsByStatus(token, status))
      );

      const index: Record<number, AdminClaim> = {};
      claimGroups.flat().forEach((claim) => {
        if (typeof claim.id === "number") {
          index[claim.id] = claim;
        }
      });
      return index;
    },
    enabled:
      isReady &&
      isAuthenticated &&
      Boolean(token) &&
      packageDetailQuery.isSuccess &&
      claimIds.length > 0,
  });

  const anonymizedViewsQuery = useQuery({
    queryKey: ["admin-package-anonymized-views", packageId, token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!packageId) {
        throw new Error("Missing package id.");
      }
      return listPackageAnonymizedViews(token, packageId);
    },
    enabled: isReady && isAuthenticated && Boolean(token) && Boolean(packageId),
  });

  const generateViewsMutation = useMutation({
    mutationFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!packageId) {
        throw new Error("Missing package id.");
      }
      return generatePackageAnonymizedViews(token, packageId);
    },
    onMutate: () => {
      setFeedback(null);
    },
    onSuccess: async () => {
      setFeedback({
        tone: "success",
        message: "Anonymized views generated successfully.",
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-package-anonymized-views", packageId, token],
      });
    },
    onError: (error) => {
      setFeedback({
        tone: "error",
        message: getErrorMessage(error, "Unable to generate anonymized views."),
      });
    },
  });

  const updatePriceMutation = useMutation({
    mutationFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      if (!packageId) {
        throw new Error("Missing package id.");
      }
      const baseInput =
        priceInput ??
        (typeof packageDetailQuery.data?.price === "number"
          ? String(packageDetailQuery.data.price)
          : "");
      const trimmed = baseInput.trim();
      if (trimmed.length === 0) {
        throw new Error("Price is required.");
      }
      const parsedPrice = Number(trimmed);
      if (!Number.isFinite(parsedPrice)) {
        throw new Error("Price must be numeric.");
      }
      if (parsedPrice < 0) {
        throw new Error("Price must be greater than or equal to 0.");
      }
      return setAdminPackagePrice(packageId, parsedPrice, token);
    },
    onMutate: () => {
      setPricingFeedback(null);
    },
    onSuccess: async (updatedPackage) => {
      setPricingFeedback({
        tone: "success",
        message: "Package price saved.",
      });
      if (typeof updatedPackage.price === "number") {
        setPriceInput(String(updatedPackage.price));
      } else {
        setPriceInput(null);
      }
      await queryClient.invalidateQueries({
        queryKey: ["admin-package-detail", packageId, token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-packages", token],
      });
    },
    onError: (error) => {
      setPricingFeedback({
        tone: "error",
        message: getErrorMessage(error, "Unable to save package price."),
      });
    },
  });

  const normalizedPackageStatus = normalizeStatus(packageDetailQuery.data?.status);
  const canEditPrice = normalizedPackageStatus === "READY";
  const pricingBlockedMessage = getPricingBlockedMessage(normalizedPackageStatus);
  const priceInputValue =
    priceInput ??
    (typeof packageDetailQuery.data?.price === "number"
      ? String(packageDetailQuery.data.price)
      : "");

  if (!isReady) {
    return (
      <main className="min-h-screen px-6 py-10">
        <div className="mx-auto w-full max-w-5xl rounded-lg border p-5">
          <p className="text-sm text-gray-600">Loading session...</p>
        </div>
      </main>
    );
  }

  if (!isAuthenticated || !token) {
    return (
      <main className="min-h-screen px-6 py-10">
        <div className="mx-auto w-full max-w-5xl rounded-lg border border-red-200 bg-red-50 p-5">
          <p className="text-sm text-red-700">
            You must be logged in as admin to view this package.
          </p>
        </div>
      </main>
    );
  }

  if (!packageId || Number.isNaN(numericPackageId)) {
    return (
      <main className="min-h-screen px-6 py-10">
        <div className="mx-auto w-full max-w-5xl rounded-lg border p-5">
          <p className="text-sm text-gray-600">Package not found.</p>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-5xl space-y-6">
        <header className="space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h1 className="text-3xl font-semibold">Package Detail</h1>
            <Link
              href="/admin/packages"
              className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
            >
              Back to Package Management
            </Link>
          </div>
          <p className="text-sm text-gray-600">Package ID: {numericPackageId}</p>
        </header>

        {packageDetailQuery.isPending ? (
          <section className="rounded-lg border p-5">
            <p className="text-sm text-gray-600">Loading package detail...</p>
          </section>
        ) : packageDetailQuery.isError ? (
          <section className="rounded-lg border p-5">
            <p className="text-sm text-red-600">
              {getErrorMessage(packageDetailQuery.error, "Package detail unavailable.")}
            </p>
          </section>
        ) : !packageDetailQuery.data ? (
          <section className="rounded-lg border p-5">
            <p className="text-sm text-gray-600">Package not found.</p>
          </section>
        ) : (
          <>
            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Package Metadata</h2>
              <div className="grid gap-2 text-sm sm:grid-cols-2">
                <p>
                  <span className="text-gray-500">Package ID:</span>{" "}
                  {packageDetailQuery.data.id}
                </p>
                <p>
                  <span className="text-gray-500">Status:</span>{" "}
                  {formatStatus(packageDetailQuery.data.status)}
                </p>
                <p>
                  <span className="text-gray-500">Total Claims:</span>{" "}
                  {packageDetailQuery.data.totalClaims ?? 0}
                </p>
                <p>
                  <span className="text-gray-500">Total Face Value:</span>{" "}
                  {formatCurrency(packageDetailQuery.data.totalFaceValue)}
                </p>
                <p>
                  <span className="text-gray-500">Price:</span>{" "}
                  {formatCurrency(packageDetailQuery.data.price)}
                </p>
                <OptionalMetaRow
                  label="Notes"
                  value={textValue(packageDetailQuery.data.notes, "")}
                />
                <OptionalMetaRow
                  label="Created"
                  value={formatDateTime(packageDetailQuery.data.createdAt)}
                />
                <OptionalNumberMetaRow
                  label="Created By User ID"
                  value={packageDetailQuery.data.createdByUserId}
                />
                <OptionalNumberMetaRow
                  label="Ruleset ID"
                  value={packageDetailQuery.data.rulesetId}
                />
                <OptionalNumberMetaRow
                  label="Ruleset Version"
                  value={packageDetailQuery.data.rulesetVersion}
                />
              </div>
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Package Pricing</h2>
              <p className="text-sm text-gray-600">
                Current Price: {formatCurrency(packageDetailQuery.data.price)}
              </p>

              {canEditPrice ? (
                <form
                  className="space-y-2"
                  onSubmit={(event) => {
                    event.preventDefault();
                    updatePriceMutation.mutate();
                  }}
                >
                  <label htmlFor="package-price" className="block text-sm font-medium">
                    Price
                  </label>
                  <div className="flex flex-wrap items-center gap-2">
                    <input
                      id="package-price"
                      type="number"
                      min="0"
                      step="0.01"
                      required
                      value={priceInputValue}
                      onChange={(event) => setPriceInput(event.target.value)}
                      className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-black sm:w-64"
                    />
                    <button
                      type="submit"
                      disabled={updatePriceMutation.isPending}
                      className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                    >
                      {updatePriceMutation.isPending
                        ? "Saving..."
                        : typeof packageDetailQuery.data.price === "number"
                          ? "Update Price"
                          : "Save Price"}
                    </button>
                  </div>
                </form>
              ) : (
                <p className="text-sm text-gray-600">{pricingBlockedMessage}</p>
              )}

              {pricingFeedback && (
                <p
                  className={`text-sm ${
                    pricingFeedback.tone === "success"
                      ? "text-green-600"
                      : "text-red-600"
                  }`}
                >
                  {pricingFeedback.message}
                </p>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Included Claims</h2>

              {claimIds.length === 0 ? (
                <p className="text-sm text-gray-600">
                  This package does not contain claims yet.
                </p>
              ) : claimIndexQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading claim summaries...</p>
              ) : (
                <>
                  {claimIndexQuery.isError && (
                    <p className="text-sm text-red-600">
                      {getErrorMessage(
                        claimIndexQuery.error,
                        "Unable to load full claim summaries. Showing fallback entries."
                      )}
                    </p>
                  )}
                  <ul className="space-y-2">
                    {claimIds.map((claimId) => (
                      <ClaimSummaryCard
                        key={claimId}
                        claimId={claimId}
                        claim={claimIndexQuery.data?.[claimId]}
                      />
                    ))}
                  </ul>
                </>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-xl font-semibold">Buyer-Safe Anonymized Views</h2>
                <button
                  type="button"
                  onClick={() => generateViewsMutation.mutate()}
                  disabled={generateViewsMutation.isPending}
                  className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                >
                  {generateViewsMutation.isPending
                    ? "Generating..."
                    : "Generate Anonymized Views"}
                </button>
              </div>

              {feedback && (
                <p
                  className={`text-sm ${
                    feedback.tone === "success" ? "text-green-600" : "text-red-600"
                  }`}
                >
                  {feedback.message}
                </p>
              )}

              {anonymizedViewsQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading anonymized views...</p>
              ) : anonymizedViewsQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    anonymizedViewsQuery.error,
                    "Unable to load anonymized views."
                  )}
                </p>
              ) : !anonymizedViewsQuery.data ||
                anonymizedViewsQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">
                  No anonymized views are available for this package yet.
                </p>
              ) : (
                <ul className="space-y-2">
                  {anonymizedViewsQuery.data.map((view, index) => (
                    <AnonymizedViewCard
                      key={`${view.claimId ?? "view"}-${index}`}
                      view={view}
                      index={index}
                    />
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
