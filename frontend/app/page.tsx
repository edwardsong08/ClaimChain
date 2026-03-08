"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthSession } from "@/hooks/use-auth-session";
import { getDashboardPathForRole } from "@/lib/role-routing";

export default function HomePage() {
  const router = useRouter();
  const { role, isAuthenticated, isReady } = useAuthSession();
  const dashboardPath = getDashboardPathForRole(role);

  useEffect(() => {
    if (!isReady) return;

    if (isAuthenticated && dashboardPath) {
      router.replace(dashboardPath);
    }
  }, [dashboardPath, isAuthenticated, isReady, router]);

  if (!isReady) {
    return (
      <main className="min-h-screen flex items-center justify-center">
        <p className="text-sm text-gray-600">Loading session...</p>
      </main>
    );
  }

  if (isAuthenticated && dashboardPath) {
    return (
      <main className="min-h-screen flex items-center justify-center">
        <p className="text-sm text-gray-600">Redirecting to dashboard...</p>
      </main>
    );
  }

  return (
    <main className="min-h-screen flex items-center justify-center">
      <h1 className="text-3xl font-semibold">ClaimChain</h1>
    </main>
  );
}
