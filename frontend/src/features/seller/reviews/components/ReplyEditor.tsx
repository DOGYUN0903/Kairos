"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useId } from "react";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { useReplyReview } from "@/features/seller/reviews/api";
import { REPLY_MAX, replySchema, type ReplyFormValues } from "@/features/seller/reviews/reply-schema";
import { toast } from "@/hooks/use-toast";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";

/** 후기 답글 입력(덮어쓰기). 기존 답글이 있으면 채워서 "답글 수정". 키 없이 처리 중 재제출만 막는다. */
export function ReplyEditor({ reviewId, existing, onDone, onCancel }: { reviewId: number; existing?: string | null; onDone: () => void; onCancel: () => void }) {
  const id = useId();
  const reply = useReplyReview();
  const form = useForm<{ content: string }, unknown, ReplyFormValues>({ resolver: zodResolver(replySchema), defaultValues: { content: existing ?? "" } });
  const { errors } = form.formState;
  const content = form.watch("content");
  const edit = !!existing;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values) => {
      await reply(reviewId, values.content);
      toast({ title: edit ? "답글을 수정했습니다" : "답글을 등록했습니다" });
      onDone();
    },
    {
      onError: (e) => {
        // 구매자가 그사이 후기를 지웠다. 서버 메시지를 알리고 입력 줄을 닫는다(목록은 reply 안에서 이미 다시 읽는다).
        if (e instanceof ApiError && e.code === "REVIEW_NOT_FOUND") onDone();
        toast({ title: resolveError(e).message, variant: "destructive" });
      },
    },
  );

  return (
    <form
      noValidate
      className="flex flex-col gap-2 py-1"
      onSubmit={(e) => {
        if (submitting) {
          e.preventDefault();
          return;
        }
        void onSubmit(e);
      }}
    >
      <label htmlFor={id} className="text-[13px] font-bold">
        {edit ? "답글 수정" : "답글 작성"}
      </label>
      <textarea
        id={id}
        rows={3}
        maxLength={REPLY_MAX + 100}
        disabled={submitting}
        placeholder="고객에게 남길 답글을 적어 주세요."
        className="input min-h-[88px] py-2"
        aria-invalid={errors.content ? true : undefined}
        aria-describedby={`${id}-hint`}
        {...form.register("content")}
      />
      <div className="flex justify-between">
        {errors.content ? (
          <span id={`${id}-hint`} role="alert" className="field-error">
            {errors.content.message}
          </span>
        ) : (
          <span id={`${id}-hint`} className="hint">
            1~500자, 공백만 입력할 수 없습니다.
          </span>
        )}
        <span className="text-xs text-text-3 tabular-nums">
          {content.trim().length} / {REPLY_MAX}
        </span>
      </div>
      <div className="flex justify-end gap-2">
        <button type="button" className="btn btn-ghost btn-sm" disabled={submitting} onClick={onCancel}>
          취소
        </button>
        <button type="submit" className="btn btn-primary btn-sm" disabled={submitting}>
          {edit ? "답글 수정" : "답글 등록"}
        </button>
      </div>
    </form>
  );
}
