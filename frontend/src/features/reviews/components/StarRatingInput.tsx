"use client";

import { useRef, type KeyboardEvent } from "react";
import { cn } from "@/lib/utils";

const LABELS = ["", "별로예요", "그저 그래요", "보통이에요", "좋아요", "아주 좋아요"] as const;

export function ratingLabel(value: number): string {
  return value >= 1 && value <= 5 ? `${value}점 · ${LABELS[value]}` : "별점을 선택해 주세요";
}

/** 별점 입력: 라디오 그룹. 방향키로 이동하면 값도 바뀐다(WAI-ARIA 라디오 그룹). */
export function StarRatingInput({ value, onChange, labelledBy, disabled }: { value: number; onChange: (v: number) => void; labelledBy?: string; disabled?: boolean }) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  function move(to: number) {
    const next = Math.min(5, Math.max(1, to));
    onChange(next);
    refs.current[next - 1]?.focus();
  }

  function onKeyDown(e: KeyboardEvent) {
    const base = value >= 1 ? value : 0;
    if (e.key === "ArrowRight" || e.key === "ArrowUp") {
      e.preventDefault();
      move(base + 1);
    } else if (e.key === "ArrowLeft" || e.key === "ArrowDown") {
      e.preventDefault();
      move(base === 0 ? 1 : base - 1);
    }
  }

  return (
    <div className="flex items-center">
      <div role="radiogroup" aria-labelledby={labelledBy} className="inline-flex gap-0.5" onKeyDown={onKeyDown}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            ref={(el) => {
              refs.current[n - 1] = el;
            }}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n}점`}
            disabled={disabled}
            // 값이 없으면 첫 별만 탭으로 들어갈 수 있다.
            tabIndex={value === n || (value < 1 && n === 1) ? 0 : -1}
            onClick={() => onChange(n)}
            className={cn("h-10 w-10 text-[30px] leading-none hover:text-[#E8A317]", n <= value ? "text-[#E8A317]" : "text-border")}
          >
            ★
          </button>
        ))}
      </div>
      <span className="ml-2.5 text-[13px] font-medium text-text-2">{ratingLabel(value)}</span>
    </div>
  );
}
