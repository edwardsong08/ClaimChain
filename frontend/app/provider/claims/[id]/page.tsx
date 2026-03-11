"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useAuthSession } from "@/hooks/use-auth-session";
import { isApprovalGateForbiddenError } from "@/lib/api-error-utils";
import { getClaimById } from "@/services/claims";
import {
  downloadClaimDocument,
  isInlineViewableDocument,
  listClaimDocuments,
  uploadClaimDocument,
} from "@/services/documents";
import type { Claim } from "@/types/claims";

const usdFormatter = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});

function formatStatus(status: string | null | undefined) {
  if (!status) return "N/A";
  return status.replaceAll("_", " ");
}

function formatCurrency(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  return usdFormatter.format(value);
}

function formatDate(value: string | null | undefined) {
  if (!value) return "N/A";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString();
}

function textValue(value: string | null | undefined) {
  if (!value || value.trim().length === 0) return "N/A";
  return value;
}

function formatBytes(value: number | null | undefined) {
  if (typeof value !== "number" || value < 0) return "N/A";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function formatPercent(value: number | null | undefined) {
  if (typeof value !== "number") return "N/A";
  const normalizedPercent = value <= 1 ? value * 100 : value;
  const roundedPercent = Math.round(normalizedPercent * 10) / 10;
  return `${roundedPercent}%`;
}

function extractStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];

  return value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

function parseExplainabilityFactors(explainabilityJson: string | null | undefined) {
  if (!explainabilityJson) return [];

  try {
    const parsed = JSON.parse(explainabilityJson) as {
      contributions?: Array<{
        reason?: string;
        delta?: number;
        ruleId?: string;
      }>;
      eligibleReasons?: string[];
    };

    const contributionItems = Array.isArray(parsed.contributions)
      ? parsed.contributions
          .map((item) => {
            const reason =
              typeof item.reason === "string" && item.reason.trim().length > 0
                ? item.reason.trim()
                : typeof item.ruleId === "string" && item.ruleId.trim().length > 0
                  ? item.ruleId.trim()
                  : null;

            if (!reason) return null;

            if (typeof item.delta === "number") {
              const sign = item.delta >= 0 ? "+" : "";
              return `${reason} (${sign}${item.delta})`;
            }
            return reason;
          })
          .filter((item): item is string => Boolean(item))
      : [];

    const eligibilityItems = extractStringArray(parsed.eligibleReasons);

    return [...contributionItems, ...eligibilityItems];
  } catch {
    return [];
  }
}

function getScoreBreakdownItems(claim: Claim) {
  const rawItems = [
    ...extractStringArray(claim.scoreBreakdown),
    ...extractStringArray(claim.scoringFactors),
    ...parseExplainabilityFactors(claim.explainabilityJson),
  ];

  const subscoreItems = [
    {
      label: "Enforceability",
      value: claim.subscoreEnforceability,
    },
    {
      label: "Documentation completeness",
      value: claim.subscoreDocumentation,
    },
    {
      label: "Collectability",
      value: claim.subscoreCollectability,
    },
    {
      label: "Operational risk",
      value: claim.subscoreOperationalRisk,
    },
  ]
    .filter((item) => typeof item.value === "number")
    .map((item) => `${item.label}: ${item.value}`);

  return Array.from(new Set([...rawItems, ...subscoreItems]));
}

function hasScoreData(claim: Claim) {
  return (
    typeof claim.scoreTotal === "number" ||
    (typeof claim.grade === "string" && claim.grade.trim().length > 0) ||
    typeof claim.extractionSuccessRate === "number"
  );
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof Error)) {
    return fallback;
  }

  try {
    const parsed = JSON.parse(error.message) as {
      message?: string;
      error?: string;
    };

    if (typeof parsed.message === "string" && parsed.message.length > 0) {
      return parsed.message;
    }

    if (typeof parsed.error === "string" && parsed.error.length > 0) {
      return parsed.error;
    }
  } catch {
    return error.message || fallback;
  }

  return error.message || fallback;
}

const DOCUMENT_TYPE_OPTIONS = [
  "INVOICE",
  "CONTRACT",
  "AUTHORIZATION",
  "ITEMIZATION",
  "PROOF_OF_SERVICE",
  "CORRESPONDENCE",
  "OTHER",
] as const;

export default function ClaimDetailPage() {
  const router = useRouter();
  const params = useParams();
  const idParam = params?.id;
  const claimId = Array.isArray(idParam) ? idParam[0] : idParam;

  const { token, isReady, isAuthenticated } = useAuthSession();
  const queryClient = useQueryClient();

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [documentType, setDocumentType] =
    useState<(typeof DOCUMENT_TYPE_OPTIONS)[number]>("INVOICE");
  const [openingDocumentId, setOpeningDocumentId] = useState<number | null>(null);
  const [downloadingDocumentId, setDownloadingDocumentId] = useState<number | null>(null);

  const claimQuery = useQuery({
    queryKey: ["provider-claim", claimId, token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in to view claim details.");
      }
      if (!claimId) {
        throw new Error("Missing claim id.");
      }
      return getClaimById(claimId, token);
    },
    enabled: isReady && isAuthenticated && Boolean(token) && Boolean(claimId),
  });

  const documentsQuery = useQuery({
    queryKey: ["provider-claim-documents", claimId, token],
    queryFn: () => {
      if (!token) {
        throw new Error("You must be logged in to view claim documents.");
      }
      if (!claimId) {
        throw new Error("Missing claim id.");
      }
      return listClaimDocuments(token, claimId);
    },
    enabled: isReady && isAuthenticated && Boolean(token) && Boolean(claimId),
  });

  const shouldRedirectForApproval =
    claimQuery.isError && isApprovalGateForbiddenError(claimQuery.error);
  const claimError = claimQuery.isError ? claimQuery.error : null;
  const claimErrorMessage = getErrorMessage(claimError, "Unable to load claim details.");

  useEffect(() => {
    if (shouldRedirectForApproval) {
      router.replace("/provider/pending-approval");
    }
  }, [router, shouldRedirectForApproval]);

  const uploadDocumentMutation = useMutation({
    mutationFn: async () => {
      if (!token) {
        throw new Error("You must be logged in to upload documents.");
      }
      if (!claimId) {
        throw new Error("Missing claim id.");
      }
      if (!selectedFile) {
        throw new Error("Please choose a file to upload.");
      }

      return uploadClaimDocument(token, {
        claimId,
        file: selectedFile,
        documentType,
      });
    },
    onSuccess: async () => {
      toast.success("Document uploaded successfully.");
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      await queryClient.invalidateQueries({
        queryKey: ["provider-claim-documents", claimId, token],
      });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Document upload failed."));
    },
  });

  const handleOpenDocument = async (documentId: number, fallbackFilename: string) => {
    if (!token) {
      toast.error("You must be logged in to view claim documents.");
      return;
    }

    setOpeningDocumentId(documentId);

    try {
      const { blob, filename, contentType } = await downloadClaimDocument(token, documentId);
      const resolvedFilename = filename || fallbackFilename || `document-${documentId}`;
      if (!isInlineViewableDocument(contentType, resolvedFilename)) {
        toast.error("This file type cannot be opened inline. Use Download.");
        return;
      }

      const objectUrl = URL.createObjectURL(blob);
      const openedWindow = window.open(objectUrl, "_blank", "noopener,noreferrer");
      if (!openedWindow) {
        toast.error("Popup blocked. Allow popups to open this document.");
      }
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch (error) {
      toast.error(getErrorMessage(error, "Unable to open document."));
    } finally {
      setOpeningDocumentId(null);
    }
  };

  const handleDownloadDocument = async (documentId: number, fallbackFilename: string) => {
    if (!token) {
      toast.error("You must be logged in to view claim documents.");
      return;
    }

    setDownloadingDocumentId(documentId);

    try {
      const { blob, filename } = await downloadClaimDocument(token, documentId);
      const resolvedFilename = filename || fallbackFilename || `document-${documentId}`;
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = resolvedFilename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch (error) {
      toast.error(getErrorMessage(error, "Unable to download document."));
    } finally {
      setDownloadingDocumentId(null);
    }
  };

  const handleUploadSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await uploadDocumentMutation.mutateAsync();
  };

  if (!isReady) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Loading session...</p>
      </main>
    );
  }

  if (!isAuthenticated || !token) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-red-600">
          You must be logged in to view claim details.
        </p>
      </main>
    );
  }

  if (!claimId) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Claim not found.</p>
      </main>
    );
  }

  if (claimQuery.isPending || shouldRedirectForApproval) {
    return <main className="min-h-screen" aria-busy="true" />;
  }

  if (claimQuery.isError) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-red-600">{claimErrorMessage}</p>
      </main>
    );
  }

  if (!claimQuery.data) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6 py-10">
        <p className="text-sm text-gray-600">Claim not found.</p>
      </main>
    );
  }

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-4xl space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Claim Details</h1>
          <p className="text-sm text-gray-600">Claim ID: {claimId ?? "N/A"}</p>
        </header>

        {!isReady ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Loading session...
          </div>
        ) : !isAuthenticated || !token ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            You must be logged in to view claim details.
          </div>
        ) : !claimId ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Claim not found.
          </div>
        ) : claimQuery.isPending ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Loading claim details...
          </div>
        ) : claimQuery.isError ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {claimErrorMessage}
          </div>
        ) : !claimQuery.data ? (
          <div className="rounded-lg border p-4 text-sm text-gray-600">
            Claim not found.
          </div>
        ) : (
          <section className="space-y-3">
            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Summary</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>Claim Type: {textValue(claimQuery.data.claimType)}</p>
                <p>Debt Type: {textValue(claimQuery.data.debtType)}</p>
                <p>
                  Jurisdiction: {textValue(claimQuery.data.jurisdictionState)}
                </p>
                <p>Date of Default: {formatDate(claimQuery.data.dateOfDefault)}</p>
                <p>Date of Service: {formatDate(claimQuery.data.dateOfService)}</p>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Status</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>Status: {formatStatus(claimQuery.data.status)}</p>
                <p>
                  Dispute Status: {textValue(claimQuery.data.disputeStatus)}
                </p>
                <p>Submitted At: {formatDate(claimQuery.data.submittedAt)}</p>
                <p>Submitted By: {textValue(claimQuery.data.submittedBy)}</p>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Financial Details</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>
                  Current Amount: {formatCurrency(claimQuery.data.currentAmount)}
                </p>
                <p>
                  Original Amount: {formatCurrency(claimQuery.data.originalAmount)}
                </p>
                <p>Amount: {formatCurrency(claimQuery.data.amount)}</p>
                <p>
                  Last Payment Date: {formatDate(claimQuery.data.lastPaymentDate)}
                </p>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Client / Debtor Details</h2>
              <div className="mt-2 grid gap-2 text-sm">
                <p>Client Name: {textValue(claimQuery.data.clientName)}</p>
                <p>Client Contact: {textValue(claimQuery.data.clientContact)}</p>
                <p>Client Address: {textValue(claimQuery.data.clientAddress)}</p>
                <p>Debtor Name: {textValue(claimQuery.data.debtorName)}</p>
                <p>Debtor Email: {textValue(claimQuery.data.debtorEmail)}</p>
                <p>Debtor Phone: {textValue(claimQuery.data.debtorPhone)}</p>
                <p>Debtor Address: {textValue(claimQuery.data.debtorAddress)}</p>
                <p>Debtor Type: {textValue(claimQuery.data.debtorType)}</p>
                <p>
                  Contact History: {textValue(claimQuery.data.contactHistory)}
                </p>
              </div>
            </div>

            {claimQuery.data.status === "APPROVED" && (
              <div className="rounded-lg border p-4">
                <h2 className="text-lg font-semibold">Claim Score</h2>
                {hasScoreData(claimQuery.data) ? (
                  <>
                    <div className="mt-2 grid gap-2 text-sm">
                      <p>
                        Score:{" "}
                        {typeof claimQuery.data.scoreTotal === "number"
                          ? claimQuery.data.scoreTotal
                          : "N/A"}
                      </p>
                      <p>Grade: {textValue(claimQuery.data.grade)}</p>
                      <p>
                        Extraction Success:{" "}
                        {formatPercent(claimQuery.data.extractionSuccessRate)}
                      </p>
                      <p>Scored At: {formatDate(claimQuery.data.scoredAt)}</p>
                      <p>Score Trigger: {textValue(claimQuery.data.scoreTrigger)}</p>
                    </div>

                    {getScoreBreakdownItems(claimQuery.data).length > 0 && (
                      <div className="mt-4">
                        <h3 className="text-sm font-semibold">Score Breakdown</h3>
                        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm">
                          {getScoreBreakdownItems(claimQuery.data).map((item) => (
                            <li key={item}>{item}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </>
                ) : (
                  <p className="mt-1 text-sm text-gray-600">
                    Scoring pending. Scores are generated after claim review.
                  </p>
                )}
              </div>
            )}

            <div className="rounded-lg border p-4">
              <h2 className="text-lg font-semibold">Documents</h2>
              <div className="mt-3 space-y-4">
                <form onSubmit={handleUploadSubmit} className="space-y-3">
                  <div>
                    <label
                      htmlFor="documentType"
                      className="mb-1 block text-sm font-medium"
                    >
                      Document Type
                    </label>
                    <select
                      id="documentType"
                      value={documentType}
                      onChange={(event) =>
                        setDocumentType(
                          event.target.value as (typeof DOCUMENT_TYPE_OPTIONS)[number]
                        )
                      }
                      className="w-full rounded-md border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-black"
                    >
                      {DOCUMENT_TYPE_OPTIONS.map((option) => (
                        <option key={option} value={option}>
                          {option.replaceAll("_", " ")}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label htmlFor="documentFile" className="mb-1 block text-sm font-medium">
                      File
                    </label>
                    <input
                      id="documentFile"
                      ref={fileInputRef}
                      type="file"
                      accept=".pdf,.png,.jpg,.jpeg,.txt"
                      onChange={(event) => {
                        setSelectedFile(event.target.files?.[0] ?? null);
                      }}
                      className="w-full rounded-md border px-3 py-2 text-sm"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={uploadDocumentMutation.isPending || !selectedFile}
                    className="rounded-md bg-black px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
                  >
                    {uploadDocumentMutation.isPending
                      ? "Uploading..."
                      : "Upload Document"}
                  </button>
                </form>

                <div>
                  <h3 className="text-sm font-semibold">Uploaded Documents</h3>

                  {documentsQuery.isPending ? (
                    <p className="mt-2 text-sm text-gray-600">
                      Loading documents...
                    </p>
                  ) : documentsQuery.isError ? (
                    <p className="mt-2 text-sm text-red-600">
                      {getErrorMessage(
                        documentsQuery.error,
                        "Unable to load documents."
                      )}
                    </p>
                  ) : !documentsQuery.data || documentsQuery.data.length === 0 ? (
                    <p className="mt-2 text-sm text-gray-600">
                      No documents uploaded yet.
                    </p>
                  ) : (
                    <ul className="mt-2 space-y-2">
                      {documentsQuery.data.map((document) => {
                        const fallbackFilename =
                          textValue(document.filename) === "N/A"
                            ? `document-${document.id}`
                            : textValue(document.filename);
                        const canOpenInline = isInlineViewableDocument(null, fallbackFilename);
                        const isOpening = openingDocumentId === document.id;
                        const isDownloading = downloadingDocumentId === document.id;

                        return (
                          <li key={document.id} className="rounded-md border p-3 text-sm">
                            <p className="font-medium">{textValue(document.filename)}</p>
                            <p>Type: {formatStatus(document.documentType)}</p>
                            <p>Status: {formatStatus(document.status)}</p>
                            <p>Extraction: {formatStatus(document.extractionStatus)}</p>
                            <p>Size: {formatBytes(document.sizeBytes)}</p>
                            <p>Uploaded: {formatDate(document.createdAt)}</p>
                            <div className="mt-2 flex flex-wrap gap-2">
                              <button
                                type="button"
                                onClick={() => {
                                  void handleOpenDocument(document.id, fallbackFilename);
                                }}
                                disabled={isOpening || isDownloading || !canOpenInline}
                                title={
                                  canOpenInline
                                    ? undefined
                                    : "This file type cannot be opened inline."
                                }
                                className="rounded-md border px-3 py-1 text-xs font-medium disabled:opacity-60"
                              >
                                {isOpening ? "Opening..." : "Open"}
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  void handleDownloadDocument(document.id, fallbackFilename);
                                }}
                                disabled={isOpening || isDownloading}
                                className="rounded-md border px-3 py-1 text-xs font-medium disabled:opacity-60"
                              >
                                {isDownloading ? "Downloading..." : "Download"}
                              </button>
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              </div>
            </div>
          </section>
        )}

        <Link href="/provider/claims" className="inline-flex text-sm underline">
          Back to My Claims
        </Link>
      </div>
    </main>
  );
}
