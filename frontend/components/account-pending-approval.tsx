type AccountPendingApprovalProps = {
  roleLabel: string;
  roleMessage: string;
};

export default function AccountPendingApproval({
  roleLabel,
  roleMessage,
}: AccountPendingApprovalProps) {
  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-2xl space-y-6 rounded-lg border p-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Account Pending Approval</h1>
          <p className="text-sm text-gray-600">
            Your {roleLabel} account must be approved by an admin before you can
            access {roleMessage}.
          </p>
        </header>
      </div>
    </main>
  );
}
