"use client";

import { useContext } from "react";
import { AuthContext, type AuthContextValue } from "@/auth/AuthProvider";

export type { Me } from "@/features/auth/api";

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth 는 AuthProvider 안에서만 쓸 수 있습니다.");
  return ctx;
}
