import Link from "next/link";

export default function ProviderDashboardPage() {
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
