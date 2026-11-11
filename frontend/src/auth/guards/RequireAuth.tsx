"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useAuth } from "@/auth/useAuth";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams().toString();

  useEffect(() => {
    if (status === "anonymous") {
      const next = pathname + (search ? `?${search}` : "");
      router.replace(`/login?next=${encodeURIComponent(next)}`);
    }
  }, [status, pathname, search, router]);

  if (status === "loading") return <PageSkeleton />;
  if (status === "anonymous") return null;
  return <>{children}</>;
}
