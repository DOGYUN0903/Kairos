"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { Pagination } from "@/components/common/Pagination";
import { useMyReviews } from "@/features/reviews/api";
import { ReviewCard } from "@/features/reviews/components/ReviewCard";
import { MyPageHead } from "@/features/mypage/components/MyPageHead";
import { parsePage } from "@/lib/page";

/** 마이페이지 내 후기. ?page= 가 진실이다. */
export function MyReviewsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const page = parsePage(useSearchParams().get("page"));
  const q = useMyReviews(page);
  const go = (p: number) => router.push(p > 0 ? `${pathname}?page=${p}` : pathname);
  const data = q.data;

  return (
    <div>
      <MyPageHead title="내 후기" aside={data ? <span className="text-sm text-text-2">{data.totalElements}개</span> : undefined} />
      {q.isError && !data ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : data.content.length === 0 ? (
        data.totalPages > 0 && page > 0 ? (
          <EmptyState message="해당 페이지에 후기가 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
        ) : (
          <EmptyState message="작성한 후기가 없습니다" action={{ label: "주문 내역 보기", href: "/my/orders" }} />
        )
      ) : (
        <>
          {data.content.map((r) => (
            <ReviewCard key={r.reviewId} review={r} />
          ))}
          <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
        </>
      )}
    </div>
  );
}
