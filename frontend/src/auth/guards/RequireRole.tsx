"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { useAuth, type Me } from "@/auth/useAuth";

function RoleCheck({ role, children }: { role: Me["role"]; children: ReactNode }) {
  const { me } = useAuth();
  const router = useRouter();
  const allowed = me?.role === role;

  useEffect(() => {
    if (me && !allowed) router.replace("/forbidden");
  }, [me, allowed, router]);

  return allowed ? <>{children}</> : null;
}

export function RequireRole({ role, children }: { role: Me["role"]; children: ReactNode }) {
  return (
    <RequireAuth>
      <RoleCheck role={role}>{children}</RoleCheck>
    </RequireAuth>
  );
}
