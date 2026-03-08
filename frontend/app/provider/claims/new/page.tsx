"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { useAuthSession } from "@/hooks/use-auth-session";
import { createClaim } from "@/services/claims";
import type { CreateClaimRequest } from "@/types/claims";

const CLAIM_TYPE_OPTIONS = [
  { label: "Services", value: "SERVICES" },
  { label: "Invoice", value: "INVOICE" },
  { label: "Rent", value: "RENT" },
  { label: "Loan", value: "LOAN" },
  { label: "Other", value: "OTHER" },
] as const;

const DEBTOR_TYPE_OPTIONS = [
  { label: "Consumer", value: "CONSUMER" },
  { label: "Business", value: "BUSINESS" },
  { label: "Government", value: "GOV" },
  { label: "Insurance", value: "INSURANCE" },
  { label: "Other", value: "OTHER" },
] as const;

const DISPUTE_STATUS_OPTIONS = [
  { label: "None", value: "NONE" },
  { label: "Possible", value: "POSSIBLE" },
  { label: "Active", value: "ACTIVE" },
  { label: "Resolved", value: "RESOLVED" },
] as const;

const CLAIM_TYPE_VALUES = CLAIM_TYPE_OPTIONS.map((option) => option.value) as [
  (typeof CLAIM_TYPE_OPTIONS)[number]["value"],
  ...(typeof CLAIM_TYPE_OPTIONS)[number]["value"][],
];

const DEBTOR_TYPE_VALUES = DEBTOR_TYPE_OPTIONS.map((option) => option.value) as [
  (typeof DEBTOR_TYPE_OPTIONS)[number]["value"],
  ...(typeof DEBTOR_TYPE_OPTIONS)[number]["value"][],
];

const DISPUTE_STATUS_VALUES = DISPUTE_STATUS_OPTIONS.map((option) => option.value) as [
  (typeof DISPUTE_STATUS_OPTIONS)[number]["value"],
  ...(typeof DISPUTE_STATUS_OPTIONS)[number]["value"][],
];

const claimFormSchema = z.object({
  debtorName: z.string().optional(),
  debtorEmail: z
    .union([z.literal(""), z.string().trim().email("Please enter a valid debtor email.")])
    .optional(),
  debtorPhone: z.string().optional(),
  debtorAddress: z.string().trim().min(1, "Debtor address is required."),
  debtorType: z.enum(DEBTOR_TYPE_VALUES),
  jurisdictionState: z
    .string()
    .trim()
    .length(2, "Use a 2-letter state code.")
    .regex(/^[A-Za-z]{2}$/, "Use a valid 2-letter state code."),
  claimType: z.enum(CLAIM_TYPE_VALUES),
  disputeStatus: z.enum(DISPUTE_STATUS_VALUES),
  clientName: z.string().trim().min(1, "Client name is required."),
  clientContact: z.string().trim().min(1, "Client contact is required."),
  clientAddress: z.string().trim().min(1, "Client address is required."),
  debtType: z.string().trim().min(1, "Debt type is required."),
  contactHistory: z.string().trim().min(1, "Contact history is required."),
  currentAmount: z
    .string()
    .trim()
    .min(1, "Current amount is required.")
    .refine((value) => {
      const parsed = Number(value);
      return !Number.isNaN(parsed) && parsed > 0;
    }, "Current amount must be greater than 0."),
  originalAmount: z
    .string()
    .trim()
    .optional()
    .refine((value) => {
      if (!value) return true;
      const parsed = Number(value);
      return !Number.isNaN(parsed) && parsed >= 0;
    }, "Original amount cannot be negative."),
  dateOfDefault: z.string().min(1, "Date of default is required."),
  dateOfService: z.string().optional(),
  lastPaymentDate: z.string().optional(),
});

type ClaimFormValues = z.infer<typeof claimFormSchema>;

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

function toOptionalString(value: string | undefined) {
  if (!value) return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function toOptionalNumber(value: string | undefined) {
  const normalized = toOptionalString(value);
  if (!normalized) return undefined;
  const parsed = Number(normalized);
  return Number.isNaN(parsed) ? undefined : parsed;
}

export default function NewProviderClaimPage() {
  const router = useRouter();
  const { token, isReady, isAuthenticated } = useAuthSession();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ClaimFormValues>({
    resolver: zodResolver(claimFormSchema),
    defaultValues: {
      debtorName: "",
      debtorEmail: "",
      debtorPhone: "",
      debtorAddress: "",
      debtorType: "CONSUMER",
      jurisdictionState: "",
      claimType: "SERVICES",
      disputeStatus: "NONE",
      clientName: "",
      clientContact: "",
      clientAddress: "",
      debtType: "",
      contactHistory: "",
      currentAmount: "",
      originalAmount: "",
      dateOfDefault: "",
      dateOfService: "",
      lastPaymentDate: "",
    },
  });

  const createClaimMutation = useMutation({
    mutationFn: (payload: CreateClaimRequest) => {
      if (!token) {
        throw new Error("You must be logged in to submit a claim.");
      }
      return createClaim(payload, token);
    },
    onSuccess: (createdClaim) => {
      toast.success("Claim submitted successfully.");
      if (createdClaim.id) {
        router.replace(`/provider/claims/${createdClaim.id}`);
        return;
      }
      router.replace("/provider/claims");
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, "Claim submission failed."));
    },
  });

  const onSubmit = async (values: ClaimFormValues) => {
    if (!token) {
      toast.error("You must be logged in to submit a claim.");
      return;
    }

    const payload: CreateClaimRequest = {
      debtorAddress: values.debtorAddress.trim(),
      debtorType: values.debtorType,
      jurisdictionState: values.jurisdictionState.trim().toUpperCase(),
      claimType: values.claimType,
      disputeStatus: values.disputeStatus,
      clientName: values.clientName.trim(),
      clientContact: values.clientContact.trim(),
      clientAddress: values.clientAddress.trim(),
      debtType: values.debtType.trim(),
      contactHistory: values.contactHistory.trim(),
      currentAmount: Number(values.currentAmount),
      dateOfDefault: values.dateOfDefault,
      debtorName: toOptionalString(values.debtorName),
      debtorEmail: toOptionalString(values.debtorEmail)?.toLowerCase(),
      debtorPhone: toOptionalString(values.debtorPhone),
      originalAmount: toOptionalNumber(values.originalAmount),
      dateOfService: toOptionalString(values.dateOfService),
      lastPaymentDate: toOptionalString(values.lastPaymentDate),
    };

    await createClaimMutation.mutateAsync(payload);
  };

  return (
    <main className="min-h-screen px-6 py-10">
      <div className="mx-auto w-full max-w-3xl space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold">Submit New Claim</h1>
          <p className="text-sm text-gray-600">
            Submit claim intake details for provider processing.
          </p>
        </header>

        {!isReady ? (
          <p className="text-sm text-gray-600">Loading session...</p>
        ) : !isAuthenticated ? (
          <p className="text-sm text-red-600">
            You must be logged in to submit claims.
          </p>
        ) : null}

        <form
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4 rounded-lg border p-6"
        >
          <div>
            <label htmlFor="debtorName" className="mb-1 block text-sm font-medium">
              Debtor Name (Optional)
            </label>
            <input
              id="debtorName"
              type="text"
              {...register("debtorName")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.debtorName && (
              <p className="mt-1 text-sm text-red-600">{errors.debtorName.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="debtorEmail" className="mb-1 block text-sm font-medium">
              Debtor Email (Optional)
            </label>
            <input
              id="debtorEmail"
              type="email"
              {...register("debtorEmail")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.debtorEmail && (
              <p className="mt-1 text-sm text-red-600">{errors.debtorEmail.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="debtorPhone" className="mb-1 block text-sm font-medium">
              Debtor Phone (Optional)
            </label>
            <input
              id="debtorPhone"
              type="text"
              {...register("debtorPhone")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.debtorPhone && (
              <p className="mt-1 text-sm text-red-600">{errors.debtorPhone.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="debtorAddress" className="mb-1 block text-sm font-medium">
              Debtor Address
            </label>
            <input
              id="debtorAddress"
              type="text"
              {...register("debtorAddress")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.debtorAddress && (
              <p className="mt-1 text-sm text-red-600">{errors.debtorAddress.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="debtorType" className="mb-1 block text-sm font-medium">
              Debtor Type
            </label>
            <select
              id="debtorType"
              {...register("debtorType")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            >
              {DEBTOR_TYPE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {errors.debtorType && (
              <p className="mt-1 text-sm text-red-600">{errors.debtorType.message}</p>
            )}
          </div>

          <div>
            <label
              htmlFor="jurisdictionState"
              className="mb-1 block text-sm font-medium"
            >
              Jurisdiction State
            </label>
            <input
              id="jurisdictionState"
              type="text"
              maxLength={2}
              placeholder="NY"
              {...register("jurisdictionState")}
              className="w-full rounded-md border px-3 py-2 uppercase outline-none focus:ring-2 focus:ring-black"
            />
            {errors.jurisdictionState && (
              <p className="mt-1 text-sm text-red-600">
                {errors.jurisdictionState.message}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="claimType" className="mb-1 block text-sm font-medium">
              Claim Type
            </label>
            <select
              id="claimType"
              {...register("claimType")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            >
              {CLAIM_TYPE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {errors.claimType && (
              <p className="mt-1 text-sm text-red-600">{errors.claimType.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="disputeStatus" className="mb-1 block text-sm font-medium">
              Dispute Status
            </label>
            <select
              id="disputeStatus"
              {...register("disputeStatus")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            >
              {DISPUTE_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {errors.disputeStatus && (
              <p className="mt-1 text-sm text-red-600">{errors.disputeStatus.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="clientName" className="mb-1 block text-sm font-medium">
              Client Name
            </label>
            <input
              id="clientName"
              type="text"
              {...register("clientName")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.clientName && (
              <p className="mt-1 text-sm text-red-600">{errors.clientName.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="clientContact" className="mb-1 block text-sm font-medium">
              Client Contact
            </label>
            <input
              id="clientContact"
              type="text"
              {...register("clientContact")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.clientContact && (
              <p className="mt-1 text-sm text-red-600">{errors.clientContact.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="clientAddress" className="mb-1 block text-sm font-medium">
              Client Address
            </label>
            <input
              id="clientAddress"
              type="text"
              {...register("clientAddress")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.clientAddress && (
              <p className="mt-1 text-sm text-red-600">{errors.clientAddress.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="debtType" className="mb-1 block text-sm font-medium">
              Debt Type
            </label>
            <input
              id="debtType"
              type="text"
              {...register("debtType")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.debtType && (
              <p className="mt-1 text-sm text-red-600">{errors.debtType.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="contactHistory" className="mb-1 block text-sm font-medium">
              Contact History
            </label>
            <textarea
              id="contactHistory"
              rows={4}
              {...register("contactHistory")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.contactHistory && (
              <p className="mt-1 text-sm text-red-600">{errors.contactHistory.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="currentAmount" className="mb-1 block text-sm font-medium">
              Current Amount
            </label>
            <input
              id="currentAmount"
              type="number"
              step="0.01"
              {...register("currentAmount")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.currentAmount && (
              <p className="mt-1 text-sm text-red-600">{errors.currentAmount.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="originalAmount" className="mb-1 block text-sm font-medium">
              Original Amount (Optional)
            </label>
            <input
              id="originalAmount"
              type="number"
              step="0.01"
              {...register("originalAmount")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.originalAmount && (
              <p className="mt-1 text-sm text-red-600">{errors.originalAmount.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="dateOfDefault" className="mb-1 block text-sm font-medium">
              Date of Default
            </label>
            <input
              id="dateOfDefault"
              type="date"
              {...register("dateOfDefault")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.dateOfDefault && (
              <p className="mt-1 text-sm text-red-600">{errors.dateOfDefault.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="dateOfService" className="mb-1 block text-sm font-medium">
              Date of Service (Optional)
            </label>
            <input
              id="dateOfService"
              type="date"
              {...register("dateOfService")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.dateOfService && (
              <p className="mt-1 text-sm text-red-600">{errors.dateOfService.message}</p>
            )}
          </div>

          <div>
            <label
              htmlFor="lastPaymentDate"
              className="mb-1 block text-sm font-medium"
            >
              Last Payment Date (Optional)
            </label>
            <input
              id="lastPaymentDate"
              type="date"
              {...register("lastPaymentDate")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.lastPaymentDate && (
              <p className="mt-1 text-sm text-red-600">
                {errors.lastPaymentDate.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={!isAuthenticated || isSubmitting || createClaimMutation.isPending}
            className="rounded-md bg-black px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {isSubmitting || createClaimMutation.isPending
              ? "Submitting..."
              : "Submit Claim"}
          </button>
        </form>

        <Link href="/provider/claims" className="inline-flex text-sm underline">
          Back to My Claims
        </Link>
      </div>
    </main>
  );
}
