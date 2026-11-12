"use client";

import { Coins } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { useQueryClient } from "@tanstack/react-query";
import { deleteReview, type ReviewResult } from "@/features/reviews/api";
import { ReviewFormDialog } from "@/features/reviews/components/ReviewFormDialog";
import { ReviewPhotos } from "@/features/reviews/components/ReviewPhotos";
import { ReviewReply } from "@/features/reviews/components/ReviewReply";
import { StarRating } from "@/features/reviews/components/StarRating";
import { toast } from "@/hooks/use-toast";
import { formatDate } from "@/lib/datetime";
import { formatPoints } from "@/lib/money";
import { queryKeys } from "@/lib/query-keys";

/** 내 후기 한 건(수정·삭제 포함). 상품 후기 목록의 읽기 전용 형태는 ProductReviewItem. */
export function ReviewCard({ review }: { review: ReviewResult }) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);

  async function remove() {
    if (deleting) return;
    setDeleting(true);
    try {
      await deleteReview(review.reviewId);
      toast({ title: "후기를 삭제했습니다" });
    } catch (e) {
      // 이미 삭제된 후기는 조용히 목록만 다시 읽는다.
      if (!(e instanceof ApiError && e.code === "REVIEW_NOT_FOUND")) toast({ title: resolveError(e).message, variant: "destructive" });
    } finally {
      setConfirming(false);
      setDeleting(false);
      await Promise.all(
        [queryKeys.reviews.mineRoot, queryKeys.reviews.productRoot(review.productId), queryKeys.catalog.product(review.productId), queryKeys.orders.root].map((queryKey) =>
          qc.invalidateQueries({ queryKey }),
        ),
      );
    }
  }

  return (
    <article className="mt-4 rounded-lg border border-border">
      <div className="flex items-center gap-3.5 rounded-t-lg border-b border-border bg-surface px-5 py-4">
        <Link className="min-w-0 truncate font-medium hover:underline" href={`/products/${review.productId}`}>
          {review.productName}
        </Link>
        <span className="ml-auto whitespace-nowrap text-[13px] text-text-2">
          <span className="tabular-nums">{formatDate(review.createdAt)}</span>
        </span>
      </div>
      <div className="p-5">
        <StarRating rating={review.rating} />
        <p className="mt-2.5 max-w-[760px] whitespace-pre-line leading-[1.75]">{review.content}</p>
        <ReviewPhotos urls={review.images.map((i) => i.url)} />
        {review.replyContent ? (
          <ReviewReply content={review.replyContent} repliedAt={review.repliedAt} sellerLabel="판매자" />
        ) : (
          <div className="mt-3.5 text-[13px] text-text-2">아직 판매자 답글이 없습니다</div>
        )}
      </div>
      <div className="flex items-center justify-between gap-3 border-t border-border px-5 py-3 text-[13px] text-text-2">
        <span className="inline-flex items-center gap-1 font-bold text-success">
          <Coins className="h-4 w-4" aria-hidden />
          {formatPoints(review.rewardPoints)} 적립
        </span>
        <span className="flex gap-2">
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditing(true)}>
            수정
          </button>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setConfirming(true)}>
            삭제
          </button>
        </span>
      </div>
      {editing && (
        <ReviewFormDialog
          mode="edit"
          reviewId={review.reviewId}
          productId={review.productId}
          product={{ name: review.productName }}
          initial={{ rating: review.rating, content: review.content, images: review.images }}
          onSubmitted={() => setEditing(false)}
          onClose={() => setEditing(false)}
        />
      )}
      <ConfirmDialog
        open={confirming}
        title="후기 삭제"
        description="삭제하면 이 상품에 후기를 다시 쓸 수 없고, 적립된 포인트는 회수되지 않습니다"
        confirmLabel="삭제"
        destructive
        onConfirm={() => void remove()}
        onCancel={() => !deleting && setConfirming(false)}
      />
    </article>
  );
}
