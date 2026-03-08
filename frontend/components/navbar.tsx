"use client";

import Link from "next/link";
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

export default function Navbar() {
  const router = useRouter();
  const { role, isAuthenticated, isReady, logout } = useAuthSession();

  const handleLogout = () => {
    logout();
    router.push("/");
  };

  return (
    <header className="border-b">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link href={isAuthenticated ? getDashboardPath(role) : "/"} className="text-xl font-semibold">
          ClaimChain
        </Link>

        <div className="flex items-center gap-4 text-sm">
          {!isReady ? null : !isAuthenticated ? (
            <>
              <Link href="/">Home</Link>
              <Link href="/login">Login</Link>
              <Link href="/signup/provider">Provider Sign Up</Link>
              <Link href="/signup/buyer">Buyer Sign Up</Link>
            </>
          ) : (
            <>
              <Link href={getDashboardPath(role)}>Dashboard</Link>
              <button
                type="button"
                onClick={handleLogout}
                className="rounded-md border px-3 py-1.5"
              >
                Logout
              </button>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}