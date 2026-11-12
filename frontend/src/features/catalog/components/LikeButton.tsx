"use client";

import { Heart } from "lucide-react";
import { useId } from "react";
import { useToggleLike } from "@/features/catalog/api";
import { cn } from "@/lib/utils";

const count = new Intl.NumberFormat("ko-KR");

/** likeCount 가 없으면 숫자를 그리지 않는다. liked 가 null/undefined(비로그인)이면 선택되지 않은 상태로 보인다. 누르면 로그인으로 이동한다. */
export function LikeButton({
  productId,
  liked,
  likeCount,
  tone = "default",
  label = "좋아요",
}: {
  productId: number;
  liked: boolean | null | undefined;
  likeCount?: number;
  /** 눌린 상태의 색. hot 은 핫딜 전용이라 상품 상세 밖(내 좋아요)에서는 navy 를 쓴다. */
  tone?: "default" | "navy";
  /** 접근성 이름. 목록에서는 상품 이름을 넣는다. */
  label?: string;
}) {
  const { toggle, isPending } = useToggleLike(productId);
  const descId = useId();
  const pressed = liked === true;
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={label}
      {...(likeCount !== undefined ? { "aria-describedby": descId } : {})}
      disabled={isPending}
      onClick={() => toggle(pressed)}
      className={cn(
        "flex h-[52px] flex-col items-center justify-center rounded border border-border bg-bg text-[11px] text-text-2 disabled:opacity-60",
        pressed && (tone === "navy" ? "border-navy text-navy" : "border-hot text-hot"),
      )}
    >
      <Heart className={cn("h-5 w-5", pressed && "fill-current")} aria-hidden />
      {likeCount !== undefined && (
        <>
          <span aria-hidden>{count.format(likeCount)}</span>
          <span id={descId} className="sr-only">
            좋아요 {count.format(likeCount)}개
          </span>
        </>
      )}
    </button>
  );
}
