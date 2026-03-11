"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuthSession } from "@/hooks/use-auth-session";
import {
  createPackage,
  getAdminPackageDetail,
  listAdminPackages,
  listClaimsByStatus,
  listPackage,
  previewPackageBuild,
  unlistPackage,
} from "@/services/admin";
import type { AdminClaimStatus, AdminPackage } from "@/types/admin";

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

function formatStatus(status: string | null | undefined) {
  if (!status) return "N/A";
  return status.replaceAll("_", " ");
}

function formatCurrency(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  return usdFormatter.format(value);
}

function formatPrice(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  return usdFormatter.format(value);
}

function formatDate(value: string | null | undefined) {
  if (!value) return "N/A";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString();
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "N/A";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
}

function textValue(value: string | null | undefined, fallback = "N/A") {
  if (!value || value.trim().length === 0) return fallback;
  return value;
}

function formatRulesetDisplay(
  rulesetId: number | null | undefined,
  rulesetVersion: number | null | undefined
) {
  if (typeof rulesetId !== "number") return "N/A";
  if (typeof rulesetVersion !== "number") return `${rulesetId}`;
  return `${rulesetId} (v${rulesetVersion})`;
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

function packageStatus(packageItem: AdminPackage) {
  return (packageItem.status ?? "").trim().toUpperCase();
}

export default function AdminPackagesPage() {
  const { token, isReady, isAuthenticated } = useAuthSession();
  const queryClient = useQueryClient();

  const [notes, setNotes] = useState("");
  const [listingPackageId, setListingPackageId] = useState<number | null>(null);
  const [unlistingPackageId, setUnlistingPackageId] = useState<number | null>(null);

  const packagesQuery = useQuery({
    queryKey: ["admin-packages", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listAdminPackages(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  const packageIdsKey = Array.isArray(packagesQuery.data)
    ? packagesQuery.data.map((packageItem) => packageItem.id).join(",")
    : "";

  const packagedClaimsQuery = useQuery({
    queryKey: ["admin-packaged-claims", token, packageIdsKey],
    queryFn: async () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      const packages = packagesQuery.data ?? [];
      if (packages.length === 0) {
        return [] as Array<{
          claimId: number;
          packageId: number;
          amount?: number | null;
          scoreTotal?: number | null;
          grade?: string | null;
        }>;
      }

      const [packageDetails, claimGroups] = await Promise.all([
        Promise.all(
          packages.map((packageItem) => getAdminPackageDetail(token, packageItem.id))
        ),
        Promise.all(claimStatuses.map((status) => listClaimsByStatus(token, status))),
      ]);

      const claimIndex = new Map(
        claimGroups
          .flat()
          .filter((claim) => typeof claim.id === "number")
          .map((claim) => [claim.id, claim])
      );

      return packageDetails.flatMap((packageDetail) => {
        const claimIds = Array.isArray(packageDetail.claimIds)
          ? packageDetail.claimIds.filter(
              (claimId): claimId is number => typeof claimId === "number"
            )
          : [];

        return claimIds.map((claimId) => {
          const claim = claimIndex.get(claimId);
          return {
            claimId,
            packageId: packageDetail.id,
            amount: claim?.currentAmount ?? claim?.amount ?? null,
            scoreTotal: claim?.scoreTotal ?? null,
            grade: claim?.grade ?? null,
          };
        });
      });
    },
    enabled:
      isReady &&
      isAuthenticated &&
      Boolean(token) &&
      packagesQuery.isSuccess &&
      Array.isArray(packagesQuery.data) &&
      packagesQuery.data.length > 0,
  });

  const previewMutation = useMutation({
    mutationFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      const normalizedNotes = notes.trim();
      return previewPackageBuild(
        token,
        normalizedNotes.length > 0 ? normalizedNotes : undefined
      );
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to preview package build."));
    },
  });

  const createPackageMutation = useMutation({
    mutationFn: () => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      const normalizedNotes = notes.trim();
      return createPackage(token, normalizedNotes.length > 0 ? normalizedNotes : undefined);
    },
    onSuccess: async (result) => {
      toast.success(`Package created${result.packageId ? ` (#${result.packageId})` : ""}.`);
      previewMutation.reset();
      await queryClient.invalidateQueries({
        queryKey: ["admin-packages", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-packaged-claims", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to create package."));
    },
  });

  const listPackageMutation = useMutation({
    mutationFn: (packageId: number) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return listPackage(packageId, token);
    },
    onSuccess: async () => {
      toast.success("Package listed.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-packages", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-packaged-claims", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to list package."));
    },
  });

  const unlistPackageMutation = useMutation({
    mutationFn: (packageId: number) => {
      if (!token) {
        throw new Error("You must be logged in as admin.");
      }
      return unlistPackage(packageId, token);
    },
    onSuccess: async () => {
      toast.success("Package unlisted.");
      await queryClient.invalidateQueries({
        queryKey: ["admin-packages", token],
      });
      await queryClient.invalidateQueries({
        queryKey: ["admin-packaged-claims", token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Unable to unlist package."));
    },
  });

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-5xl space-y-6">
        <header className="space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h1 className="text-3xl font-semibold">Package Management</h1>
            <Link
              href="/admin/dashboard"
              className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
            >
              Back to Dashboard
            </Link>
          </div>
          <p className="text-sm text-gray-600">
            Build and manage packages using the currently active PACKAGING ruleset.
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
              <h2 className="text-xl font-semibold">Build / Preview</h2>
              <p className="text-sm text-gray-600">
                Preview and build packages from claims eligible under the ACTIVE PACKAGING
                ruleset only.
              </p>

              <div className="space-y-2">
                <label htmlFor="package-notes" className="block text-sm font-medium">
                  Notes (optional)
                </label>
                <textarea
                  id="package-notes"
                  value={notes}
                  onChange={(event) => setNotes(event.target.value)}
                  rows={3}
                  className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-black"
                  placeholder="Optional package notes."
                />
              </div>

              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={previewMutation.isPending || createPackageMutation.isPending}
                  onClick={() => previewMutation.mutate()}
                  className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                >
                  {previewMutation.isPending ? "Running Preview..." : "Run Preview"}
                </button>
                <button
                  type="button"
                  disabled={previewMutation.isPending || createPackageMutation.isPending}
                  onClick={() => createPackageMutation.mutate()}
                  className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                >
                  {createPackageMutation.isPending ? "Creating..." : "Create Package"}
                </button>
              </div>

              {previewMutation.isPending ? (
                <p className="text-sm text-gray-600">Running package preview...</p>
              ) : previewMutation.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(previewMutation.error, "Package preview failed.")}
                </p>
              ) : previewMutation.data ? (
                <div className="rounded-md border p-3 text-sm">
                  <p>Buildable: {previewMutation.data.buildable ? "Yes" : "No"}</p>
                  <p>Status: {textValue(previewMutation.data.status)}</p>
                  <p>Total Claims: {previewMutation.data.totalClaims ?? 0}</p>
                  <p>Total Face Value: {formatCurrency(previewMutation.data.totalFaceValue)}</p>
                  <p>
                    Ruleset:{" "}
                    {formatRulesetDisplay(
                      previewMutation.data.rulesetId,
                      previewMutation.data.rulesetVersion
                    )}
                  </p>

                  {!previewMutation.data.buildable &&
                    Array.isArray(previewMutation.data.failureReasons) &&
                    previewMutation.data.failureReasons.length > 0 && (
                      <div className="mt-3">
                        <p className="font-medium">Failure Reasons</p>
                        <ul className="mt-1 list-disc space-y-1 pl-5">
                          {previewMutation.data.failureReasons.map((reason) => (
                            <li key={reason}>{reason}</li>
                          ))}
                        </ul>
                      </div>
                    )}

                  {Array.isArray(previewMutation.data.claimIds) &&
                    previewMutation.data.claimIds.length > 0 && (
                      <div className="mt-3">
                        <p className="font-medium">Included Claims</p>
                        <ul className="mt-1 list-disc space-y-1 pl-5">
                          {previewMutation.data.claimIds.slice(0, 20).map((claimId) => (
                            <li key={claimId}>Claim #{claimId}</li>
                          ))}
                        </ul>
                      </div>
                    )}

                  {previewMutation.data.buildable &&
                    (previewMutation.data.totalClaims ?? 0) === 0 && (
                      <p className="mt-2 text-gray-600">
                        No valid package candidates are available right now.
                      </p>
                    )}
                </div>
              ) : (
                <p className="text-sm text-gray-600">
                  Run a preview to see candidate claims and ruleset usage before creating a
                  package.
                </p>
              )}

              {createPackageMutation.isError && (
                <p className="text-sm text-red-600">
                  {getErrorMessage(createPackageMutation.error, "Package creation failed.")}
                </p>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Existing Packages</h2>

              {packagesQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading packages...</p>
              ) : packagesQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(packagesQuery.error, "Unable to load packages.")}
                </p>
              ) : packagesQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">No packages exist yet.</p>
              ) : (
                <ul className="space-y-2">
                  {packagesQuery.data.map((packageItem) => {
                    const status = packageStatus(packageItem);
                    const canList = status === "READY";
                    const canUnlist = status === "LISTED";

                    return (
                      <li key={packageItem.id} className="rounded-md border p-3">
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                          <div className="space-y-1 text-sm">
                            <p>Package ID: {packageItem.id}</p>
                            <p>Status: {formatStatus(packageItem.status)}</p>
                            <p>Total Claims: {packageItem.totalClaims ?? 0}</p>
                            <p>Total Face Value: {formatCurrency(packageItem.totalFaceValue)}</p>
                            <p>
                              Price:{" "}
                              {formatPrice(packageItem.price)}
                            </p>
                            <p>
                              Ruleset:{" "}
                              {formatRulesetDisplay(
                                packageItem.rulesetId,
                                packageItem.rulesetVersion
                              )}
                            </p>
                            <p>Created: {formatDate(packageItem.createdAt)}</p>
                            {status === "SOLD" && (
                              <>
                                <p>Purchaser: {textValue(packageItem.purchaserEmail)}</p>
                                <p>Purchased: {formatDateTime(packageItem.purchasedAt)}</p>
                              </>
                            )}
                          </div>

                          <div className="flex flex-wrap gap-2">
                            <Link
                              href={`/admin/packages/${packageItem.id}`}
                              className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
                            >
                              View Package
                            </Link>

                            {canList && (
                              <button
                                type="button"
                                disabled={
                                  listPackageMutation.isPending &&
                                  listingPackageId === packageItem.id
                                }
                                onClick={() => {
                                  setListingPackageId(packageItem.id);
                                  listPackageMutation.mutate(packageItem.id, {
                                    onSettled: () => setListingPackageId(null),
                                  });
                                }}
                                className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                              >
                                {listPackageMutation.isPending &&
                                listingPackageId === packageItem.id
                                  ? "Listing..."
                                  : "List Package"}
                              </button>
                            )}

                            {canUnlist && (
                              <button
                                type="button"
                                disabled={
                                  unlistPackageMutation.isPending &&
                                  unlistingPackageId === packageItem.id
                                }
                                onClick={() => {
                                  setUnlistingPackageId(packageItem.id);
                                  unlistPackageMutation.mutate(packageItem.id, {
                                    onSettled: () => setUnlistingPackageId(null),
                                  });
                                }}
                                className="rounded-md border px-3 py-1.5 text-sm font-medium disabled:opacity-60"
                              >
                                {unlistPackageMutation.isPending &&
                                unlistingPackageId === packageItem.id
                                  ? "Unlisting..."
                                  : "Unlist Package"}
                              </button>
                            )}

                            {!canList && !canUnlist && (
                              <span className="inline-flex items-center text-xs text-gray-500">
                                No list/unlist action available
                              </span>
                            )}
                          </div>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>

            <section className="space-y-3 rounded-lg border p-5">
              <h2 className="text-xl font-semibold">Packaged Claims</h2>

              {packagesQuery.isPending || packagedClaimsQuery.isPending ? (
                <p className="text-sm text-gray-600">Loading packaged claims...</p>
              ) : packagesQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    packagesQuery.error,
                    "Unable to load packages for packaged claims."
                  )}
                </p>
              ) : packagedClaimsQuery.isError ? (
                <p className="text-sm text-red-600">
                  {getErrorMessage(
                    packagedClaimsQuery.error,
                    "Unable to load packaged claims."
                  )}
                </p>
              ) : !packagedClaimsQuery.data || packagedClaimsQuery.data.length === 0 ? (
                <p className="text-sm text-gray-600">
                  No claims are currently included in packages.
                </p>
              ) : (
                <ul className="space-y-2">
                  {packagedClaimsQuery.data.map((entry) => (
                    <li
                      key={`${entry.packageId}-${entry.claimId}`}
                      className="rounded-md border p-3 text-sm"
                    >
                      <div className="grid gap-1 sm:grid-cols-2">
                        <p>Claim ID: {entry.claimId}</p>
                        <p>Package ID: {entry.packageId}</p>
                        {typeof entry.amount === "number" && (
                          <p>Amount: {formatCurrency(entry.amount)}</p>
                        )}
                        {typeof entry.scoreTotal === "number" && (
                          <p>Score: {entry.scoreTotal}</p>
                        )}
                        {textValue(entry.grade, "").length > 0 && (
                          <p>Grade: {textValue(entry.grade)}</p>
                        )}
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
