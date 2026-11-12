"use client";

import { Suspense, type ReactNode } from "react";
import { RequireRole } from "@/auth/guards/RequireRole";
import { useAuth } from "@/auth/useAuth";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useQueueCounts } from "@/features/admin/review/api";
import { usePathname } from "next/navigation";
import { ConsoleShell, type NavItem } from "@/components/layout/ConsoleShell";
import { adminTitleOf } from "@/components/layout/console-titles";

const nav = (reviewBadge: number | undefined): NavItem[] => [
  { href: "/admin", label: "대시보드" },
  { href: "/admin/review", label: "검수", ...(reviewBadge ? { badge: reviewBadge } : {}) },
  { href: "/admin/categories", label: "카테고리" },
  { href: "/admin/sellers", label: "판매자" },
  { href: "/admin/orders", label: "주문" },
  { href: "/admin/coupons", label: "쿠폰 관리" },
  { href: "/admin/members/points", label: "회원 포인트" },
  { href: "/admin/hotdeals", label: "핫딜", disabled: true, soonLabel: "준비 중" },
];

function Shell({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  const title = adminTitleOf(usePathname());
  // 검수 배지 = 4개 큐 대기 수의 합. 이 컴포넌트는 RequireRole(ADMIN) 안에서만 그려진다.
  const { total } = useQueueCounts();
  return (
    <ConsoleShell role="관리자 콘솔" nav={nav(total)} userLabel={me?.name ?? ""} {...(title ? { title } : {})}>
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
