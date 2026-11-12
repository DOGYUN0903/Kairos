"use client";

import { Info } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/auth/useAuth";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { useClaimablePolicies, useMyCoupons } from "@/features/coupons/api";
import { COUPON_STATUSES, parseCouponStatus, parseCouponTab, type CouponStatus, type CouponTab } from "@/features/coupons/coupon-params";
import { ClaimableCouponCard } from "@/features/coupons/components/ClaimableCouponList";
import { MyCouponCard } from "@/features/coupons/components/MyCouponCard";
import { MyPageHead } from "@/features/mypage/components/MyPageHead";
import { parsePage } from "@/lib/page";
import { cn } from "@/lib/utils";

const STATUS_LABEL: Record<CouponStatus, string> = { AVAILABLE: "사용 가능", USED: "사용 완료", EXPIRED: "기간 만료" };
const EMPTY_MINE: Record<CouponStatus, string> = {
  AVAILABLE: "사용할 수 있는 쿠폰이 없습니다.",
  USED: "사용한 쿠폰이 없습니다.",
  EXPIRED: "기간이 지난 쿠폰이 없습니다.",
};

function href(pathname: string, tab: CouponTab, status: CouponStatus, page: number): string {
  const q = new URLSearchParams();
  if (tab === "mine") {
    q.set("tab", "mine");
    if (status !== "AVAILABLE") q.set("status", status);
    if (page > 0) q.set("page", String(page));
  }
  const s = q.toString();
  return s ? `${pathname}?${s}` : pathname;
}

function Note({ children }: { children: string }) {
  return (
    <p className="mt-4 flex items-center gap-2 text-[13px] text-text-2">
      <Info className="h-4 w-4 shrink-0" aria-hidden />
      {children}
    </p>
  );
}

function ClaimableTab() {
  const { status } = useAuth();
  const q = useClaimablePolicies(undefined, status === "authenticated");
  if (q.isError) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />;
  if (!q.data) return <PageSkeleton />;
  return (
    <>
      <Note>전 상품에 쓸 수 있는 쿠폰과 장바구니 쿠폰입니다. 특정 상품·판매자 쿠폰은 상품 상세에서 받을 수 있습니다.</Note>
      {q.data.content.length === 0 ? (
        <EmptyState message="지금 받을 수 있는 쿠폰이 없습니다." />
      ) : (
        <ul className="mt-5 grid grid-cols-2 gap-4">
          {q.data.content.map((p) => (
            <li key={p.policyId}>
              <ClaimableCouponCard policy={p} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function MineTab({ status, page, go }: { status: CouponStatus; page: number; go: (status: CouponStatus, page: number) => void }) {
  const q = useMyCoupons({ status, page });
  // 렌더 중 시계를 읽지 않도록 마운트 시각을 고정한다("곧 만료"는 7일 단위라 충분하다).
  const [nowMs] = useState(() => Date.now());
  const data = q.data;
  return (
    <>
      <div role="group" aria-label="쿠폰 상태" className="mt-5 flex gap-2">
        {COUPON_STATUSES.map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={s === status}
            onClick={() => go(s, 0)}
            className={cn(
              "min-h-9 rounded-[18px] border px-4 font-medium",
              s === status ? "border-navy bg-navy font-bold text-white" : "border-border bg-bg text-text-2 hover:border-navy",
            )}
          >
            {STATUS_LABEL[s]}
          </button>
        ))}
      </div>
      <Note>사용할 수 있는 쿠폰은 주문서에서 자동으로 보입니다</Note>
      {q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : data.content.length === 0 ? (
        data.totalPages > 0 && page > 0 ? (
          <EmptyState message="해당 페이지에 쿠폰이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(status, 0) }} />
        ) : (
          <EmptyState message={EMPTY_MINE[status]} />
        )
      ) : (
        <>
          <ul className="mt-4 grid grid-cols-2 gap-4">
            {data.content.map((c) => (
              <li key={c.couponId}>
                <MyCouponCard coupon={c} nowMs={nowMs} />
              </li>
            ))}
          </ul>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go(status, p)} />
        </>
      )}
    </>
  );
}

/** 마이페이지 쿠폰. ?tab=(claimable|mine), 내 쿠폰은 ?status=·?page= 가 진실이다. */
export function CouponsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const tab = parseCouponTab(sp);
  const status = parseCouponStatus(sp);
  const page = parsePage(sp.get("page"));

  const tabs: { id: CouponTab; label: string }[] = [
    { id: "claimable", label: "받을 수 있는 쿠폰" },
    { id: "mine", label: "내 쿠폰" },
  ];

  return (
    <div>
      <MyPageHead title="쿠폰" />
      <div role="tablist" aria-label="쿠폰 구분" className="mt-6 flex gap-1 border-b border-border">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={t.id === tab}
            onClick={() => router.push(href(pathname, t.id, "AVAILABLE", 0))}
            className={cn(
              "-mb-px min-h-11 border-b-2 px-4 font-medium",
              t.id === tab ? "border-navy font-bold text-navy" : "border-transparent text-text-2",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel">
        {tab === "claimable" ? (
          <ClaimableTab />
        ) : (
          <MineTab status={status} page={page} go={(s, p) => router.push(href(pathname, "mine", s, p))} />
        )}
      </div>
    </div>
  );
}
