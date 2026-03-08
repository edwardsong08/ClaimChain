export function getDashboardPathForRole(
  role: string | null | undefined
): string | null {
  if (!role) return null;

  const normalizedRole = role.trim().toUpperCase();
  if (!normalizedRole) return null;

  switch (normalizedRole) {
    case "PROVIDER":
    case "SERVICE_PROVIDER":
      return "/provider/dashboard";
    case "BUYER":
    case "COLLECTION_AGENCY":
      return "/buyer/dashboard";
    case "ADMIN":
      return "/admin/dashboard";
    default:
      return null;
  }
}
