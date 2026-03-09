export function canShowRescore(status) {
  const normalizedStatus = (status ?? "").trim().toUpperCase();
  return (
    normalizedStatus !== "UNDER_REVIEW" &&
    normalizedStatus !== "REVIEW" &&
    normalizedStatus !== "SUBMITTED"
  );
}
