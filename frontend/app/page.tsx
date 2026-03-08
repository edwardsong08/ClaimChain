"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthSession } from "@/hooks/use-auth-session";

function getDashboardPath(role: string | null) {
  switch (role) {
    case "SERVICE_PROVIDER":
      return "/provider/dashboard";
    case "COLLECTION_AGENCY":
      return "/buyer/dashboard";
    case "ADMIN":
      return "/admin/dashboard";
    default:
      return "/";
  }
}

export default function HomePage() {
  const router = useRouter();
  const { role, isAuthenticated, isReady } = useAuthSession();

  useEffect(() => {
    if (!isReady) return;

    if (isAuthenticated) {
      router.replace(getDashboardPath(role));
    }
  }, [isAuthenticated, isReady, role, router]);

  if (!isReady) return null;

  if (isAuthenticated) return null;

  return (
    <main className="min-h-screen flex items-center justify-center">
      <h1 className="text-3xl font-semibold">ClaimChain</h1>
    </main>
  );
}