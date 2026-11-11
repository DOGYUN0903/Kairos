"use client";

import { Suspense, type ReactNode } from "react";
import { RequireRole } from "@/auth/guards/RequireRole";
import { useAuth } from "@/auth/useAuth";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { ConsoleShell, type NavItem } from "@/components/layout/ConsoleShell";

// 검수 배지(대기 건수)는 Task 13 에서 붙는다.
const nav: NavItem[] = [
  { href: "/admin", label: "대시보드" },
  { href: "/admin/review", label: "검수" },
  { href: "/admin/categories", label: "카테고리" },
  { href: "/admin/sellers", label: "판매자" },
  { href: "/admin/hotdeals", label: "핫딜", disabled: true, soonLabel: "준비 중" },
];

function Shell({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  return (
    <ConsoleShell role="관리자 콘솔" nav={nav} userLabel={me?.name ?? ""}>
      {children}
    </ConsoleShell>
  );
}

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <RequireRole role="ADMIN">
        <Shell>{children}</Shell>
      </RequireRole>
    </Suspense>
  );
}
