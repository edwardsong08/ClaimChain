function normalizeClaimStatus(status) {
  return (status ?? "").trim().toUpperCase();
}

export function isReviewStateStatus(status) {
  const normalizedStatus = normalizeClaimStatus(status);
  return normalizedStatus === "UNDER_REVIEW" || normalizedStatus === "REVIEW";
}

export function canShowRescore(status) {
  const normalizedStatus = normalizeClaimStatus(status);
  return !isReviewStateStatus(normalizedStatus) && normalizedStatus !== "SUBMITTED";
}
