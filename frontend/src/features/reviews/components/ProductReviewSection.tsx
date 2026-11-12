"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { useProductReviews, type ReviewResult } from "@/features/reviews/api";
import { ReviewPhotos } from "@/features/reviews/components/ReviewPhotos";
import { ReviewReply } from "@/features/reviews/components/ReviewReply";
import { StarRating, StarRatingAverage } from "@/features/reviews/components/StarRating";
import { formatDate } from "@/lib/datetime";
import { parsePage } from "@/lib/page";

const count = new Intl.NumberFormat("ko-KR");

/** 상품 후기 목록의 읽기 전용 한 건. */
export function ProductReviewItem({ review }: { review: ReviewResult }) {
  return (
    <article className="border-b border-border py-6">
      <div className="flex flex-wrap items-center gap-3">
        <StarRating rating={review.rating} />
        <span className="font-medium">{review.writerName}</span>
        <span className="text-xs tabular-nums text-text-3">{formatDate(review.createdAt)}</span>
      </div>
      <p className="mt-2.5 max-w-[760px] whitespace-pre-line leading-[1.75]">{review.content}</p>
      <ReviewPhotos urls={review.images.map((i) => i.url)} />
      {review.replyContent && <ReviewReply content={review.replyContent} repliedAt={review.repliedAt} />}
    </article>
  );
}

/**
 * 상품 상세의 후기 영역. 요약(평균·개수)은 상품 상세 응답의 서버 값이고, 목록은 이 영역 안에서만 로딩·오류를 보인다.
 * 페이지는 ?reviewPage= (0부터), 넘겨도 화면이 맨 위로 튀지 않게 scroll:false 로 바꾼다.
 */
export function ProductReviewSection({ productId, reviewCount, averageRating }: { productId: number; reviewCount: number; averageRating: number }) {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const page = parsePage(sp.get("reviewPage"));
  const q = useProductReviews(productId, page);
  const data = q.data;

  const go = (p: number) => {
    const next = new URLSearchParams(sp.toString());
    if (p > 0) next.set("reviewPage", String(p));
    else next.delete("reviewPage");
    const s = next.toString();
    router.replace(`${pathname}${s ? `?${s}` : ""}#reviews`, { scroll: false });
  };

  return (
    <section id="reviews" aria-labelledby="reviews-h" className="mx-auto mt-10 max-w-[960px] pb-16">
      <h2 id="reviews-h" className="text-xl font-bold">
        상품 후기 <span className="font-medium text-text-2">{count.format(reviewCount)}</span>
      </h2>
      {reviewCount === 0 ? (
        <p className="mt-6 py-10 text-center text-text-2">아직 후기가 없습니다</p>
      ) : (
        <>
          <div className="mt-5 flex items-center gap-5 rounded-lg bg-surface px-6 py-5">
            <div className="text-[40px] font-bold leading-none tabular-nums">{averageRating.toFixed(1)}</div>
            <div className="flex flex-col gap-1.5">
              <StarRatingAverage average={averageRating} />
              <span className="text-text-2">후기 {count.format(reviewCount)}개</span>
            </div>
          </div>
          {q.isError && !data ? (
            <ErrorState error={q.error} onRetry={() => void q.refetch()} />
          ) : !data ? (
            <div aria-busy="true" aria-label="후기를 불러오는 중" className="mt-6 flex flex-col gap-4">
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-20 w-full" />
            </div>
          ) : data.content.length === 0 ? (
            <p className="mt-6 py-10 text-center text-text-2">이 페이지에 후기가 없습니다</p>
          ) : (
            <>
              <div className="mt-2">
                {data.content.map((r) => (
                  <ProductReviewItem key={r.reviewId} review={r} />
                ))}
              </div>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
            </>
          )}
        </>
      )}
    </section>
  );
}
