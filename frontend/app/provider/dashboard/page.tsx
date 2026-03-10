"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useAuthSession } from "@/hooks/use-auth-session";
import { isApprovalGateForbiddenError } from "@/lib/api-error-utils";
import { listClaims } from "@/services/claims";

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

export default function ProviderDashboardPage() {
  const router = useRouter();
  const { token, isReady, isAuthenticated } = useAuthSession();

  const approvalProbeQuery = useQuery({
    queryKey: ["provider-dashboard-approval-probe", token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in to access the provider dashboard.");
      }
      return listClaims(token);
    },
    enabled: isReady && isAuthenticated && Boolean(token),
    retry: false,
  });

  const shouldRedirectForApproval =
    approvalProbeQuery.isError &&
    isApprovalGateForbiddenError(approvalProbeQuery.error);

  useEffect(() => {
    if (shouldRedirectForApproval) {
      router.replace("/provider/pending-approval");
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
          You must be logged in to access the provider dashboard.
        </p>
      </main>
    );
  }

  if (approvalProbeQuery.isPending || shouldRedirectForApproval) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Checking account approval...</p>
      </main>
    );
  }

  if (approvalProbeQuery.isError) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-red-600">
          {getErrorMessage(
            approvalProbeQuery.error,
            "Unable to load provider dashboard."
          )}
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-4xl space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Provider Dashboard</h1>
          <p className="text-sm text-gray-600">
            Manage your submitted claims and start new claim intake from one
            place.
          </p>
        </header>

        <section className="flex flex-wrap gap-3">
          <Link
            href="/provider/claims"
            className="rounded-md bg-black px-4 py-2 text-sm font-medium text-white"
          >
            View Claims
          </Link>
          <Link
            href="/provider/claims/new"
            className="rounded-md border px-4 py-2 text-sm font-medium"
          >
            Submit New Claim
          </Link>
        </section>
      </div>
    </main>
  );
}
