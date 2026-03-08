export type UserRole = "SERVICE_PROVIDER" | "COLLECTION_AGENCY" | "ADMIN";

export type LoginRequest = {
  email: string;
  password: string;
};

export type RegisterRequest = {
  email: string;
  password: string;
  role: "SERVICE_PROVIDER" | "COLLECTION_AGENCY";
};

export type AuthResponse = {
  token: string;
  role: UserRole;
};