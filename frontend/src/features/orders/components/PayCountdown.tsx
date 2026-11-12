"use client";

import { useEffect, useRef, useState } from "react";
import { formatCountdown, secondsUntilKst } from "@/lib/datetime";
import { floorDiv, mod } from "@/lib/money";
import { cn } from "@/lib/utils";

function useNow(): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

/** 결제 기한까지 남은 시간(1초 간격). 0 이 되면 onExpire 를 한 번 부른다(같은 expiresAt 에 대해). */
export function PayCountdown({ expiresAt, size = "lg", onExpire }: { expiresAt: string; size?: "sm" | "lg"; onExpire?: () => void }) {
  const now = useNow();
  const remaining = secondsUntilKst(expiresAt, now);
  const fired = useRef<string | null>(null);
  const onExpireRef = useRef(onExpire);
  onExpireRef.current = onExpire;

  useEffect(() => {
    if (remaining === 0 && fired.current !== expiresAt) {
      fired.current = expiresAt;
      onExpireRef.current?.();
    }
  }, [remaining, expiresAt]);

  return (
    <div
      role="timer"
      aria-label={`결제까지 남은 시간 ${floorDiv(remaining, 60)}분 ${mod(remaining, 60)}초`}
      className={size === "sm" ? "flex items-baseline justify-center gap-2" : "text-right"}
    >
      <span className={cn("text-xs text-text-2", size === "lg" && "block")}>남은 시간</span>
      <strong className={cn("font-bold tabular-nums", size === "lg" ? "text-[28px] leading-tight tracking-wide" : "text-lg")}>{formatCountdown(remaining)}</strong>
    </div>
  );
}
