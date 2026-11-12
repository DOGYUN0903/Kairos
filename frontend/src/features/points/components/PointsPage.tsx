"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { MyPageHead } from "@/features/mypage/components/MyPageHead";
import { usePointTransactions } from "@/features/points/api";
import { PointSummaryCard } from "@/features/points/components/PointSummaryCard";
import { PointTransactionTable } from "@/features/points/components/PointTransactionTable";
import { parsePage } from "@/lib/page";

/** 마이페이지 포인트. ?page= 가 진실이다. */
export function PointsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const page = parsePage(useSearchParams().get("page"));
  const q = usePointTransactions(page);
  const data = q.data;
  const go = (p: number) => router.push(p > 0 ? `${pathname}?page=${p}` : pathname);

  return (
    <div>
      <MyPageHead title="포인트" />
      <PointSummaryCard />
      <h2 className="mt-8 text-lg font-bold">포인트 내역</h2>
      {q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : data.content.length === 0 ? (
        data.totalPages > 0 && page > 0 ? (
          <EmptyState message="해당 페이지에 내역이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
        ) : (
          <EmptyState message="포인트 내역이 없습니다" />
        )
      ) : (
        <>
          <PointTransactionTable rows={data.content} showOrderLink />
          <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
        </>
      )}
    </div>
  );
}
