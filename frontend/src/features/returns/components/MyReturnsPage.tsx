"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { MyPageHead } from "@/features/mypage/components/MyPageHead";
import { useMyReturns } from "@/features/returns/api";
import { ReturnCard } from "@/features/returns/components/ReturnCard";
import { parsePage } from "@/lib/page";

/** 마이페이지 반품 내역. ?page= 가 진실이다. */
export function MyReturnsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const page = parsePage(useSearchParams().get("page"));
  const q = useMyReturns(page);
  const data = q.data;
  const go = (p: number) => router.push(p > 0 ? `${pathname}?page=${p}` : pathname);

  return (
    <div>
      <MyPageHead title="반품 내역" />
      {q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : data.content.length === 0 ? (
        data.totalPages > 0 && page > 0 ? (
          <EmptyState message="해당 페이지에 반품이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
        ) : (
          <EmptyState message="반품 내역이 없습니다" action={{ label: "주문 내역 보기", href: "/my/orders" }} />
        )
      ) : (
        <>
          <p className="mt-4 mb-4 text-[13px] text-text-2">요청한 반품을 최신순으로 보여 드립니다. 환불 금액은 판매자가 상품을 받은 뒤 확정됩니다.</p>
          <div className="flex flex-col gap-3">
            {data.content.map((r) => (
              <ReturnCard key={r.returnId} r={r} />
            ))}
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
        </>
      )}
    </div>
  );
}
