"use client";

import { ChevronRight, Ticket } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/auth/useAuth";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useClaimablePolicies } from "@/features/coupons/api";
import { ClaimableCouponList } from "@/features/coupons/components/ClaimableCouponList";
import { safeNext } from "@/features/auth/redirect";

/** 이 수보다 많으면 앞의 2장만 보이고 나머지는 "쿠폰 모두 보기" 창에서 본다(시안). */
export const INLINE_LIMIT = 3;
const INLINE_WHEN_COLLAPSED = 2;

function Head({ count }: { count?: number }) {
  return (
    <div className="mb-2.5 flex items-center justify-between">
      <h2 className="flex items-center gap-1.5 text-sm font-bold">
        <Ticket className="h-4 w-4" aria-hidden />
        받을 수 있는 쿠폰
        {count !== undefined && <span className="font-medium text-text-2">{count}</span>}
      </h2>
    </div>
  );
}

/** 상품 상세의 쿠폰 받기 영역. 비로그인이면 API 를 부르지 않고 로그인 링크만 보인다. 쿠폰이 없으면 그리지 않는다. */
export function ProductCouponSection({ productId }: { productId: number }) {
  const { status } = useAuth();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const query = useClaimablePolicies(productId, status === "authenticated");

  const zone = "mt-5 rounded-lg border border-border bg-surface p-4";

  if (status === "loading") return null;
  if (status === "anonymous") {
    const next = encodeURIComponent(safeNext(pathname) ?? "/");
    return (
      <section aria-label="쿠폰" className={zone}>
        <Head />
        <div className="flex items-center justify-between gap-3 text-[13px] text-text-2">
          <span>로그인하면 이 상품에 쓸 수 있는 쿠폰을 받을 수 있습니다</span>
          <Link href={`/login?next=${next}`} className="font-bold text-navy underline underline-offset-2">
            로그인하고 쿠폰 받기
          </Link>
        </div>
      </section>
    );
  }

  if (query.isPending) {
    return (
      <section aria-label="쿠폰" aria-busy="true" className={zone}>
        <Skeleton className="h-20 w-full" />
      </section>
    );
  }
  if (query.isError) {
    return (
      <section aria-label="쿠폰" className={zone}>
        <Head />
        <p role="alert" className="flex items-center justify-between text-[13px] text-text-2">
          쿠폰을 불러오지 못했습니다.
          <button type="button" className="btn btn-sm" onClick={() => void query.refetch()}>
            다시 시도
          </button>
        </p>
      </section>
    );
  }

  const policies = query.data.content;
  if (policies.length === 0) return null;
  const collapsed = policies.length > INLINE_LIMIT;
  const shown = collapsed ? policies.slice(0, INLINE_WHEN_COLLAPSED) : policies;

  return (
    <section aria-label="쿠폰" className={zone}>
      <Head count={policies.length} />
      <ClaimableCouponList policies={shown} small />
      {collapsed && (
        <>
          <button type="button" className="btn btn-ghost btn-block mt-2 min-h-10" onClick={() => setOpen(true)}>
            쿠폰 모두 보기 ({policies.length}장)
            <ChevronRight className="h-4 w-4" aria-hidden />
          </button>
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogContent className="max-w-[520px]">
              <DialogHeader>
                <DialogTitle>받을 수 있는 쿠폰 {policies.length}</DialogTitle>
                <DialogDescription className="sr-only">이 상품에서 받을 수 있는 쿠폰 목록입니다.</DialogDescription>
              </DialogHeader>
              <div className="max-h-[440px] overflow-auto">
                <ClaimableCouponList policies={policies} small />
              </div>
            </DialogContent>
          </Dialog>
        </>
      )}
    </section>
  );
}
