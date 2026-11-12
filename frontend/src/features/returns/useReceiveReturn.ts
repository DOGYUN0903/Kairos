"use client";

import { useEffect, useRef, useState } from "react";
import { receiveOutcome } from "@/features/seller/returns/receive-outcome";
import { toast } from "@/hooks/use-toast";
import { formatKrw } from "@/lib/money";

/** whileStatus: 상세가 이 상태인 동안만 보인다(상태가 바뀌면 낡은 안내이므로 사라진다). */
export type ReturnNotice = { tone: "info" | "warn"; text: string; whileStatus: string };

export const RECEIVE_REJECTED = "환불이 거절되었습니다. 반품은 승인 상태로 돌아갔습니다. 잠시 후 다시 시도해 주세요";
export const RECEIVE_UNKNOWN = "결과를 확인하지 못했습니다. 반품 상태를 확인해 주세요";
export const RECEIVE_UNKNOWN_NOTICE = "결과를 확인하지 못했습니다. 아래 상태를 확인한 뒤 판단해 주세요. 자동으로 다시 시도하지 않습니다.";

type Args = {
  /** 지금 화면에 보이는(서버가 알려 준) 반품 상태. */
  status: string;
  /** 수거 확인(환불 실행) 호출. 판매자·관리자 api 훅의 것을 넘긴다. */
  receive: () => Promise<{ status: string; refundAmount: number }>;
  onNotice: (n: ReturnNotice | null) => void;
  /** 서버가 답한 오류(수거 확인이 환불 거절·미확인이 아닌 오류로 끝났을 때). */
  onError: (e: unknown) => void;
};

/**
 * 수거 확인(환불 실행)의 안전 규칙을 판매자·관리자 화면이 함께 쓴다.
 * - 한 번에 하나만 보낸다(ref 가드는 state 반영 전의 연타도 막는다). 응답을 못 받아도 자동으로 다시 보내지 않는다.
 * - 환불이 접수(완료·확인 중)된 뒤에는 서버 상태를 따른다: sent(접수, 아직 APPROVED 가 아닌 상태를 못 봄) →
 *   moved(APPROVED 가 아닌 상태를 봄) → 다시 APPROVED 가 보이면(PG 거절을 대사가 되돌림) 잠금을 풀고 거절 안내를 보인다.
 * 훅은 조기 반환보다 먼저 불러야 한다.
 */
export function useReceiveReturn({ status, receive, onNotice, onError }: Args) {
  const [busy, setBusy] = useState(false);
  const [lock, setLock] = useState<"none" | "sent" | "moved">("none");
  const inFlight = useRef(false);

  useEffect(() => {
    if (lock === "sent" && status !== "APPROVED") setLock("moved");
    else if (lock === "moved" && status === "APPROVED") {
      setLock("none");
      onNotice({ tone: "warn", text: RECEIVE_REJECTED, whileStatus: "APPROVED" });
    }
  }, [lock, status, onNotice]);

  // 한 번에 하나만 보낸다. 승인·거절·중단 같은 다른 처리도 이 가드를 쓴다.
  const guarded = async (fn: () => Promise<void>) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await fn();
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  /** done: 결과가 무엇이든 끝나면 부른다(대화상자를 닫는다). */
  const confirmReceive = (done: () => void) =>
    guarded(async () => {
      onNotice(null);
      let outcome;
      try {
        const res = await receive();
        outcome = receiveOutcome({ ok: true, status: res.status, refundAmount: res.refundAmount });
      } catch (e) {
        outcome = receiveOutcome({ ok: false, error: e });
        if (outcome.kind === "error") onError(e);
      }
      switch (outcome.kind) {
        case "completed":
          setLock("sent");
          toast({ title: `환불을 완료했습니다. 환불 금액 ${formatKrw(outcome.refundAmount)}` });
          break;
        case "refunding":
          setLock("sent");
          toast({ title: "환불을 확인하고 있습니다" });
          break;
        case "rejected":
          toast({ title: RECEIVE_REJECTED, variant: "destructive" });
          onNotice({ tone: "warn", text: RECEIVE_REJECTED, whileStatus: "APPROVED" });
          break;
        case "unknown":
          toast({ title: RECEIVE_UNKNOWN, variant: "destructive" });
          onNotice({ tone: "warn", text: RECEIVE_UNKNOWN_NOTICE, whileStatus: "APPROVED" });
          break;
        case "error":
          break;
      }
      done();
    });

  return { busy, locked: lock !== "none", guarded, confirmReceive };
}
