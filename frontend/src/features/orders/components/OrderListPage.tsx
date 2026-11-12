"use client";

import { useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { useMyOrders } from "@/features/orders/api";
import { parsePage } from "@/lib/page";
import { OrderCard } from "@/features/orders/components/OrderCard";
import { queryKeys } from "@/lib/query-keys";

/** 주문 내역. ?page=(0부터)가 페이지의 진실이다. */
export function OrderListPage() {
  const router = useRouter();
  const pathname = usePathname();
  const page = parsePage(useSearchParams().get("page"));
  const qc = useQueryClient();
  const q = useMyOrders(page);

  const go = (p: number) => router.push(p > 0 ? `${pathname}?page=${p}` : pathname);
  const data = q.data;

  return (
    <div className="container max-w-[1040px] pb-16 pt-9">
      <div className="mb-5 flex items-end justify-between">
        <h1 className="text-[26px] font-bold tracking-tight">주문 내역</h1>
        {data && data.totalElements > 0 && <span className="text-[13px] text-text-2">전체 {data.totalElements}건</span>}
      </div>
      {q.isError && !data ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : data.content.length === 0 ? (
        data.totalPages > 0 && page > 0 ? (
          <EmptyState message="해당 페이지에 주문이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
        ) : (
          <EmptyState message="주문 내역이 없습니다. 첫 주문을 시작해 보세요." action={{ label: "상품 보러 가기", href: "/products" }} />
        )
      ) : (
        <>
          <div className="flex flex-col gap-4">
            {data.content.map((o) => (
              <OrderCard key={o.orderId} order={o} onExpire={() => void qc.invalidateQueries({ queryKey: queryKeys.orders.list() })} />
            ))}
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
        </>
      )}
    </div>
  );
}
