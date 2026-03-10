"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useAuthSession } from "@/hooks/use-auth-session";
import { isApprovalGateForbiddenError } from "@/lib/api-error-utils";
import { listBuyerPackages } from "@/services/buyer";

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

function statusForDisplay() {
  return "LISTED";
}

export default function BuyerDashboardPage() {
  const router = useRouter();
  const { token, isReady, isAuthenticated } = useAuthSession();

  const packagesQuery = useQuery({
    queryKey: ["buyer-packages", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in as buyer.");
      }
      return listBuyerPackages(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
  });

  const shouldRedirectForApproval =
    packagesQuery.isError && isApprovalGateForbiddenError(packagesQuery.error);

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
          You must be logged in as buyer to view marketplace packages.
        </p>
      </main>
    );
  }

  if (packagesQuery.isPending || shouldRedirectForApproval) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Checking account approval...</p>
      </main>
    );
  }

  if (packagesQuery.isError) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-red-600">
          {getErrorMessage(packagesQuery.error, "Failed to load listed packages.")}
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-5xl space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Buyer Marketplace</h1>
          <p className="text-sm text-gray-600">
            Browse currently listed claim packages available for review.
          </p>
        </header>

        <section className="space-y-3 rounded-lg border p-5">
          <h2 className="text-xl font-semibold">Listed Packages</h2>

          {packagesQuery.data.length === 0 ? (
            <p className="text-sm text-gray-600">No listed packages are available right now.</p>
          ) : (
            <ul className="space-y-2">
              {packagesQuery.data.map((pkg) => (
                <li key={pkg.id} className="rounded-md border p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div className="space-y-1 text-sm">
                      <p>Package ID: {pkg.id}</p>
                      <p>Status: {statusForDisplay()}</p>
                      <p>Total Claims: {pkg.totalClaims ?? 0}</p>
                      <p>Total Face Value: {formatCurrency(pkg.totalFaceValue)}</p>
                      <p>Created: {formatDate(pkg.createdAt)}</p>
                    </div>
                    <Link
                      href={`/buyer/packages/${pkg.id}`}
                      className="inline-flex rounded-md border px-3 py-1.5 text-sm font-medium"
                    >
                      View Package
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </main>
  );
}
