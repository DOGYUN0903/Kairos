"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Fragment, useState } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { StatusChip } from "@/components/common/StatusChip";
import { Skeleton } from "@/components/ui/skeleton";
import { ReviewPhotos } from "@/features/reviews/components/ReviewPhotos";
import { StarRating } from "@/features/reviews/components/StarRating";
import { SELLER_REVIEW_PAGE_SIZE, useSellerReviews } from "@/features/seller/reviews/api";
import { ReplyEditor } from "@/features/seller/reviews/components/ReplyEditor";
import { formatDate } from "@/lib/datetime";
import { parsePage } from "@/lib/page";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-top";

/** 판매자 후기 관리(최신순). ?page= 가 진실이다. 줄을 펴서 답글을 쓴다. */
export function SellerReviewsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const page = parsePage(useSearchParams().get("page"));
  const q = useSellerReviews(page);
  const [openId, setOpenId] = useState<number | null>(null);

  const go = (p: number) => {
    setOpenId(null);
    router.push(p > 0 ? `${pathname}?page=${p}` : pathname, { scroll: false });
  };
  const data = q.data;
  const rows = data?.content ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-bold">후기 관리</h2>
        <p className="mt-2 text-[13px] text-text-2">최신순입니다. 답글이 없는 후기는 &quot;미답변&quot;으로 표시됩니다.</p>
      </div>

      <div className="card">
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 후기가 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
          ) : (
            <EmptyState message="받은 후기가 없습니다." />
          )
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr>
                    <th scope="col" className={TH}>상품</th>
                    <th scope="col" className={TH}>별점</th>
                    <th scope="col" className={TH}>작성자</th>
                    <th scope="col" className={TH}>후기</th>
                    <th scope="col" className={TH}>작성일</th>
                    <th scope="col" className={TH}>답글</th>
                    <th scope="col" className={TH}>
                      <span className="sr-only">작업</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => {
                    const open = openId === r.reviewId;
                    const answered = !!r.replyContent;
                    return (
                      <Fragment key={r.reviewId}>
                        <tr className={answered ? undefined : "bg-[#FFFCF2]"}>
                          <td className={`${TD} min-w-[180px] font-medium`}>{r.productName}</td>
                          <td className={`${TD} whitespace-nowrap`}>
                            <StarRating rating={r.rating} />
                          </td>
                          <td className={`${TD} tabular-nums`}>{r.writerName}</td>
                          <td className={`${TD} max-w-[360px]`}>
                            <p className="line-clamp-3 whitespace-pre-line">{r.content}</p>
                            <ReviewPhotos urls={r.images.map((i) => i.url)} />
                          </td>
                          <td className={`${TD} whitespace-nowrap text-xs tabular-nums text-text-2`}>{formatDate(r.createdAt)}</td>
                          <td className={TD}>{answered ? <span className="chip chip-onsale">답변 완료</span> : <StatusChip kind="reply" status="UNANSWERED" />}</td>
                          <td className={TD}>
                            <button
                              type="button"
                              className={answered ? "btn btn-ghost btn-sm" : "btn btn-primary btn-sm"}
                              aria-expanded={open}
                              onClick={() => setOpenId(open ? null : r.reviewId)}
                            >
                              {open ? "접기" : answered ? "답글 수정" : "답글 달기"}
                            </button>
                          </td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={7} className="border-b border-border bg-surface px-3.5 py-3">
                              <ReplyEditor reviewId={r.reviewId} existing={r.replyContent} onDone={() => setOpenId(null)} onCancel={() => setOpenId(null)} />
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between px-3.5 pb-4">
              <span className="text-xs text-text-2">
                {data.totalElements}건 중 {data.page * SELLER_REVIEW_PAGE_SIZE + 1}–{data.page * SELLER_REVIEW_PAGE_SIZE + rows.length}
              </span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
