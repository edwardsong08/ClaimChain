"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthSession } from "@/hooks/use-auth-session";
import { getDashboardPathForRole } from "@/lib/role-routing";

export default function Navbar() {
  const router = useRouter();
  const { role, isAuthenticated, isReady, logout } = useAuthSession();
  const dashboardPath = getDashboardPathForRole(role);

  const handleLogout = () => {
    logout();
    router.replace("/");
  };

  return (
    <header className="border-b">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link
          href={isAuthenticated && dashboardPath ? dashboardPath : "/"}
          className="text-xl font-semibold"
        >
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
              {dashboardPath && <Link href={dashboardPath}>Dashboard</Link>}
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
