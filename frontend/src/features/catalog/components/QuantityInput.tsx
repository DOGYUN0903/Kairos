"use client";

import { Minus, Plus } from "lucide-react";
import { useId, useState } from "react";

const MAX_QTY = 999;

/** 1 이상 정수. 입력 중에는 빈 문자열을 허용하고, 포커스를 잃으면 1 로 되돌린다. */
export function QuantityInput({ value, onChange }: { value: number; onChange: (qty: number) => void }) {
  const id = useId();
  const [draft, setDraft] = useState<string | null>(null);
  const clamp = (n: number) => Math.min(MAX_QTY, Math.max(1, n));
  const btn = "grid h-9 w-9 place-items-center disabled:text-text-3";

  return (
    <div role="group" aria-label="수량" className="inline-flex rounded border border-border bg-bg">
      <button type="button" className={btn} aria-label="수량 줄이기" disabled={value <= 1} onClick={() => onChange(clamp(value - 1))}>
        <Minus className="h-4 w-4" aria-hidden />
      </button>
      <label className="sr-only" htmlFor={id}>
        수량
      </label>
      <input
        id={id}
        inputMode="numeric"
        className="w-10 border-0 bg-transparent text-center focus:outline-none"
        value={draft ?? String(value)}
        onChange={(e) => {
          const raw = e.target.value;
          if (raw === "") {
            setDraft("");
            return;
          }
          if (!/^\d{1,3}$/.test(raw)) return;
          setDraft(null);
          const n = Number(raw);
          if (n >= 1) onChange(clamp(n));
          else setDraft(raw);
        }}
        onBlur={() => setDraft(null)}
      />
      <button type="button" className={btn} aria-label="수량 늘리기" disabled={value >= MAX_QTY} onClick={() => onChange(clamp(value + 1))}>
        <Plus className="h-4 w-4" aria-hidden />
      </button>
    </div>
  );
}
