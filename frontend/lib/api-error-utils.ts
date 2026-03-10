type ParsedApiError = {
  code?: string;
  message?: string;
  error?: string;
  details?: string[];
};

function nonEmptyString(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function parseApiError(error: unknown): ParsedApiError | null {
  if (!(error instanceof Error)) {
    return null;
  }

  const rawMessage = error.message?.trim();
  if (!rawMessage) {
    return null;
  }

  try {
    const parsed = JSON.parse(rawMessage) as unknown;
    if (!parsed || typeof parsed !== "object") {
      return null;
    }

    const payload = parsed as Record<string, unknown>;
    const details = Array.isArray(payload.details)
      ? payload.details
          .map((value) => nonEmptyString(value))
          .filter((value): value is string => Boolean(value))
      : undefined;

    const normalized: ParsedApiError = {
      code: nonEmptyString(payload.code),
      message: nonEmptyString(payload.message),
      error: nonEmptyString(payload.error),
      details,
    };

    if (
      !normalized.code &&
      !normalized.message &&
      !normalized.error &&
      (!normalized.details || normalized.details.length === 0)
    ) {
      return null;
    }

    return normalized;
  } catch {
    return null;
  }
}

export function isApprovalGateForbiddenError(error: unknown): boolean {
  const parsed = parseApiError(error);
  if (!parsed) {
    return false;
  }

  if ((parsed.code ?? "").toUpperCase() !== "FORBIDDEN") {
    return false;
  }

  const combined = [
    parsed.message,
    parsed.error,
    ...(parsed.details ?? []),
  ]
    .filter((value): value is string => Boolean(value))
    .join(" ")
    .toLowerCase();

  if (!combined) {
    return false;
  }

  return (
    combined.includes("not verified") ||
    combined.includes("verification") ||
    combined.includes("approval") ||
    combined.includes("pending") ||
    combined.includes("permission") ||
    combined.includes("access denied") ||
    combined.includes("not authorized") ||
    combined.includes("forbidden")
  );
}
