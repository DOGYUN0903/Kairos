"use client";

import { useEffect, useRef } from "react";
import { pointsDisabledReason, pointsMessage, validatePoints } from "@/features/checkout/points-input";
import { formatPoints } from "@/lib/money";

/** 입력이 멈춘 뒤 견적을 다시 부르기까지의 지연(ms). */
export const POINTS_DEBOUNCE_MS = 500;

type Props = {
  /** 보유 포인트. 아직 모르면 undefined. */
  balance: number | undefined;
  /** 견적의 maxUsablePoints(서버가 잔액·한도·단위를 모두 반영한 값). */
  maxUsable: number;
  /** 입력 칸의 문자열(위에서 들고 있어 결제 가능 여부 판단에 쓴다). */
  raw: string;
  onRawChange: (raw: string) => void;
  /** 견적에 반영된 포인트. */
  applied: number;
  /** 검증을 통과한 값을 선택에 반영한다. */
  onCommit: (points: number) => void;
};

export function PointsField({ balance, maxUsable, raw, onRawChange, applied, onCommit }: Props) {
  // 잔액을 모르면 "없음"이라 단정하지 않는다(일반 안내가 나오게 0 이 아닌 값으로 본다).
  const disabledReason = pointsDisabledReason(balance ?? 1, maxUsable);
  const check = validatePoints(raw, maxUsable);
  const commitRef = useRef(onCommit);
  commitRef.current = onCommit;

  // 올바른 값이 입력이 멈춘 뒤에만 견적을 부른다. 잘못된 값은 견적을 부르지 않는다.
  const points = check.ok ? check.points : null;
  useEffect(() => {
    if (points === null || points === applied) return;
    const t = setTimeout(() => commitRef.current(points), POINTS_DEBOUNCE_MS);
    return () => clearTimeout(t);
  }, [points, applied]);

  const errorId = "points-err";
  const hintId = "points-hint";
  const error = !check.ok ? pointsMessage(check.reason, maxUsable) : null;

  return (
    <div className="flex min-w-0 flex-col gap-2">
      <div className="flex items-center gap-2">
        <label className="sr-only" htmlFor="points-input">
          사용할 포인트
        </label>
        <input
          id="points-input"
          className="input max-w-[200px] text-right tabular-nums"
          type="text"
          inputMode="numeric"
          placeholder="0"
          autoComplete="off"
          value={raw}
          disabled={disabledReason !== null}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : disabledReason ? hintId : undefined}
          onChange={(e) => onRawChange(e.target.value)}
          // Enter 가 주문 제출로 이어지지 않게 한다.
          onKeyDown={(e) => e.key === "Enter" && e.preventDefault()}
        />
        <span className="text-text-2">P</span>
        <button
          type="button"
          className="btn"
          disabled={disabledReason !== null}
          onClick={() => {
            onRawChange(String(maxUsable));
            onCommit(maxUsable);
          }}
        >
          최대 사용
        </button>
      </div>
      {error && (
        <span id={errorId} role="alert" className="text-xs text-danger">
          {error}
        </span>
      )}
      {error && <span className="text-xs text-text-2">입력이 올바르지 않아 금액은 바뀌지 않았습니다</span>}
      {disabledReason ? (
        <span id={hintId} className="text-xs text-text-2">
          {disabledReason}
        </span>
      ) : (
        <span className="text-xs text-text-2">
          최대 {formatPoints(maxUsable)}까지 사용할 수 있습니다 · 쿠폰을 바꾸면 한도도 달라집니다
        </span>
      )}
    </div>
  );
}
