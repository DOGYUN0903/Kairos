"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { editReview, writeReview, type ReviewImage } from "@/features/reviews/api";
import { ReviewImagePicker } from "@/features/reviews/components/ReviewImagePicker";
import { StarRatingInput } from "@/features/reviews/components/StarRatingInput";
import { rewardNotice } from "@/features/reviews/review-state";
import { contentLength, REVIEW_CONTENT_MAX, reviewFormSchema, type ReviewFormValues } from "@/features/reviews/schemas";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

type Common = {
  productId: number;
  product: { name: string; optionDisplayName?: string; thumbnailUrl?: string | null };
  initial?: { rating: number; content: string; images: ReviewImage[] };
  /** 저장에 성공했다(부모가 창을 닫는다). */
  onSubmitted: () => void;
  /** 저장하지 않고 닫는다(취소, 이미 작성됨·대상 아님 같은 409 포함). */
  onClose: () => void;
};
type Props = Common & ({ mode: "create"; orderId: number; orderItemId: number } | { mode: "edit"; reviewId: number });

const FORM_ERRORS = new Set(["REVIEW_INVALID", "INVALID_REQUEST", "FILE_NOT_OWNED"]);
const count = new Intl.NumberFormat("ko-KR");

/** 후기 작성·수정 창. 마운트되어 있는 동안 열려 있다(부모가 열 때만 그린다). */
export function ReviewFormDialog(props: Props) {
  const { mode, productId, product, initial, onSubmitted, onClose } = props;
  const qc = useQueryClient();
  const ratingId = useId();
  const contentId = useId();
  const [images, setImages] = useState<ReviewImage[]>(initial?.images ?? []);
  const [uploading, setUploading] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<ReviewFormValues>({
    resolver: zodResolver(reviewFormSchema),
    defaultValues: { rating: initial?.rating ?? 0, content: initial?.content ?? "", imageFileIds: (initial?.images ?? []).map((i) => i.fileId) },
  });
  const { register, control, formState, watch, setValue } = form;

  const invalidate = (...keys: readonly (readonly unknown[])[]) => Promise.all(keys.map((queryKey) => qc.invalidateQueries({ queryKey })));
  const productKeys = [queryKeys.reviews.mineRoot, queryKeys.reviews.productRoot(productId), queryKeys.catalog.product(productId)] as const;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      setFormError(null);
      const body = { rating: values.rating, content: values.content, imageFileIds: values.imageFileIds };
      if (props.mode === "create") {
        const res = await writeReview(props.orderId, props.orderItemId, body, key);
        toast({ title: rewardNotice(res.rewardPoints) });
        await invalidate(queryKeys.orders.detail(props.orderId), queryKeys.reviews.mineRoot, queryKeys.points.root, queryKeys.reviews.productRoot(productId), queryKeys.catalog.product(productId));
      } else {
        await editReview(props.reviewId, body);
        toast({ title: "후기를 수정했습니다" });
        await invalidate(...productKeys);
      }
      onSubmitted();
    },
    {
      onError: async (e) => {
        const code = e instanceof ApiError ? e.code : null;
        if (props.mode === "create" && (code === "REVIEW_ALREADY_WRITTEN" || code === "REVIEW_NOT_ELIGIBLE")) {
          toast({ title: resolveError(e).message, variant: "destructive" });
          onClose();
          await invalidate(queryKeys.orders.detail(props.orderId));
          return;
        }
        if (props.mode === "edit" && code === "REVIEW_NOT_FOUND") {
          toast({ title: resolveError(e).message, variant: "destructive" });
          onClose();
          await invalidate(...productKeys);
          return;
        }
        if (code && FORM_ERRORS.has(code)) {
          setFormError(resolveError(e).message);
          return;
        }
        toast({ title: resolveError(e).message, variant: "destructive" });
      },
    },
  );

  const content = watch("content");
  const errors = formState.errors;
  const blocked = uploading || submitting;
  const isCreate = mode === "create";

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-h-[92vh] max-w-[520px] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isCreate ? "후기 작성" : "후기 수정"}</DialogTitle>
          <DialogDescription className="sr-only">별점과 후기 내용을 입력하고 사진을 더할 수 있습니다.</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-3 border-b border-border pb-4">
          <OrderThumb url={product.thumbnailUrl} size={56} />
          <div className="min-w-0">
            <div className="truncate font-medium">{product.name}</div>
            {product.optionDisplayName && <div className="text-[13px] text-text-2">{product.optionDisplayName}</div>}
          </div>
        </div>

        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            // Enter 로도 제출되므로 버튼 비활성만으로는 부족하다.
            if (blocked) {
              e.preventDefault();
              return;
            }
            void onSubmit(e);
          }}
        >
          {formError && (
            <div role="alert" className="notice notice-warn">
              <span>{formError}</span>
            </div>
          )}
          <div className="field">
            <span className="text-[13px] font-medium" id={ratingId}>
              별점<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
            </span>
            <Controller control={control} name="rating" render={({ field }) => <StarRatingInput value={field.value} onChange={(v) => field.onChange(v)} labelledBy={ratingId} disabled={submitting} />} />
            {errors.rating && <span className="field-error">{errors.rating.message}</span>}
          </div>
          <div className="field">
            <label htmlFor={contentId}>
              후기 내용<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
              <span className="sr-only"> (필수)</span>
            </label>
            <textarea
              id={contentId}
              rows={5}
              disabled={submitting}
              placeholder="상품은 어땠나요? 10자 이상 적어 주세요."
              className="input min-h-[110px] py-2"
              aria-invalid={errors.content ? true : undefined}
              {...register("content")}
            />
            <div className="flex justify-between">
              <span className="field-error">{errors.content?.message ?? <span className="hint">앞뒤 공백을 뺀 10~1,000자</span>}</span>
              <span className="counter">
                {count.format(contentLength(content))}/{count.format(REVIEW_CONTENT_MAX)}
              </span>
            </div>
          </div>
          <div className="field">
            <span className="text-[13px] font-medium">
              사진 <span className="font-normal text-text-2">(선택, 최대 3장)</span>
            </span>
            <ReviewImagePicker
              value={images}
              disabled={submitting}
              onBusyChange={setUploading}
              onChange={(next) => {
                setImages(next);
                setValue("imageFileIds", next.map((i) => i.fileId));
              }}
            />
          </div>
          <div className="notice">
            <span>{isCreate ? "글 후기 100P, 사진 후기 500P. 작성 후 사진을 추가해도 추가 적립은 없습니다" : "수정해도 추가 적립은 없습니다"}</span>
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={onClose}>
              취소
            </button>
            <button type="submit" className="btn btn-primary" disabled={blocked}>
              {isCreate ? "후기 등록" : "수정 완료"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
