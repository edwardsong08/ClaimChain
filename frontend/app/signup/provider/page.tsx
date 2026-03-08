"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { register as registerUser } from "@/services/auth";
import { setAuthSession } from "@/lib/auth-storage";

const signupSchema = z
  .object({
    email: z.string().email("Please enter a valid email address."),
    password: z.string().min(8, "Password must be at least 8 characters."),
    confirmPassword: z.string().min(1, "Please confirm your password."),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match.",
    path: ["confirmPassword"],
  });

type ProviderSignupFormValues = z.infer<typeof signupSchema>;

export default function ProviderSignupPage() {
  const router = useRouter();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProviderSignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const onSubmit = async (data: ProviderSignupFormValues) => {
    try {
      const result = await registerUser({
        email: data.email,
        password: data.password,
        role: "SERVICE_PROVIDER",
      });

      setAuthSession(result.token, result.role);
      toast.success("Provider account created.");
      router.replace("/provider/dashboard");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Signup failed.";
      toast.error(message);
    }
  };

  return (
    <main className="min-h-screen flex items-center justify-center px-6">
      <div className="w-full max-w-md rounded-lg border p-8 shadow-sm">
        <h1 className="text-2xl font-semibold mb-2">Provider Sign Up</h1>
        <p className="text-sm text-gray-600 mb-6">
          Register as a service provider to submit claims.
        </p>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium mb-1">
              Email
            </label>
            <input
              id="email"
              type="email"
              {...register("email")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.email && (
              <p className="mt-1 text-sm text-red-600">{errors.email.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium mb-1">
              Password
            </label>
            <input
              id="password"
              type="password"
              {...register("password")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.password && (
              <p className="mt-1 text-sm text-red-600">
                {errors.password.message}
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="confirmPassword"
              className="block text-sm font-medium mb-1"
            >
              Confirm Password
            </label>
            <input
              id="confirmPassword"
              type="password"
              {...register("confirmPassword")}
              className="w-full rounded-md border px-3 py-2 outline-none focus:ring-2 focus:ring-black"
            />
            {errors.confirmPassword && (
              <p className="mt-1 text-sm text-red-600">
                {errors.confirmPassword.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full rounded-md bg-black text-white py-2 font-medium disabled:opacity-60"
          >
            {isSubmitting ? "Creating account..." : "Create Provider Account"}
          </button>
        </form>

        <div className="mt-6 text-sm text-gray-600">
          <p>
            Already have an account? <a href="/login" className="underline">Login</a>
          </p>
        </div>
      </div>
    </main>
  );
}