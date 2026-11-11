"use client";

import { Suspense, type ReactNode } from "react";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { useAuth } from "@/auth/useAuth";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { ConsoleShell, type NavItem } from "@/components/layout/ConsoleShell";

// Task 10 의 SellerGate 가 판매자 상태에 따라 메뉴·배너를 바꾼다. 이 Task 에서는 자리만 둔다.
function SellerGate({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

const nav: NavItem[] = [
  { href: "/seller", label: "대시보드" },
  { href: "/seller/products", label: "상품 관리" },
  { href: "/seller/brands", label: "브랜드" },
  { href: "/seller/settlements", label: "정산", disabled: true, soonLabel: "준비 중" },
];

function Shell({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  return (
    <ConsoleShell role="판매자 센터" nav={nav} userLabel={me?.name ?? ""}>
      <SellerGate>{children}</SellerGate>
    </ConsoleShell>
  );
}

export default function SellerLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <RequireAuth>
        <Shell>{children}</Shell>
      </RequireAuth>
    </Suspense>
  );
}
