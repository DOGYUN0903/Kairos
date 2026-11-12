import { useEffect, useState } from "react";

/** 1초마다 갱신되는 현재 시각(ms). enabled 가 false 면 타이머를 돌리지 않는다(바깥에서 시각을 받는 경우). */
export function useNow(enabled = true): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!enabled) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [enabled]);
  return now;
}
