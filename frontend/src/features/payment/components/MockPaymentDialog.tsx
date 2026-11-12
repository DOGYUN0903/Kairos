"use client";

import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Money } from "@/components/common/Money";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { mockPaymentKey, type MockOutcome, type MockOutcomePrefixes } from "@/features/payment/payment-key";
import { mockSuccessHref } from "@/features/payment/payment-urls";

// 리터럴 비교식이어야 Next 가 운영 빌드에서 분기와 import() 청크를 함께 제거한다(pnpm check:bundle 이 검증).
// 결과 선택지와 실패 접두사는 features/demo 에만 있고, 여기서는 정적으로 import 하지 않는다.
const MockOutcomePicker =
  process.env.NEXT_PUBLIC_DEMO_MODE === "true" ? dynamic(() => import("@/features/demo/MockOutcomePicker")) : null;

type Props = {
  open: boolean;
  onClose: () => void;
  orderId: number;
  orderNo: string;
  orderName: string;
  /** 서버가 준 주문 총액. */
  amount: number;
};

/** 모의 결제창. "결제"는 paymentKey 를 만들어 승인 화면으로 보내고, "닫기"는 아무것도 호출하지 않는다(주문은 결제 대기로 남는다). */
export function MockPaymentDialog({ open, onClose, orderId, orderNo, orderName, amount }: Props) {
  const router = useRouter();
  const [choice, setChoice] = useState<{ outcome: MockOutcome; prefixes?: MockOutcomePrefixes }>({ outcome: "approve" });
  const [paying, setPaying] = useState(false);
  const paid = useRef(false);

  function pay() {
    // 연타해도 paymentKey 는 하나만 만든다.
    if (paid.current) return;
    paid.current = true;
    setPaying(true);
    const paymentKey = mockPaymentKey(choice.outcome, crypto.randomUUID(), choice.prefixes);
    router.push(mockSuccessHref(orderId, orderNo, paymentKey, amount));
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && !paying && onClose()}>
      <DialogContent className="w-[440px] max-w-full gap-0 rounded-lg border-0 p-0 shadow-[0_16px_48px_rgba(15,23,42,.28)] sm:rounded-lg">
        <div className="flex items-center gap-2 px-6 pt-5">
          <DialogTitle className="text-lg font-bold">결제</DialogTitle>
          <span className="inline-flex h-5 items-center rounded-sm border border-border px-1.5 text-[11px] font-bold leading-none text-text-2">
            모의 결제
          </span>
        </div>
        <DialogDescription className="sr-only">모의 결제창입니다. 결제를 누르면 결제 승인을 요청합니다.</DialogDescription>
        <div className="flex flex-col gap-4 px-6 pb-1 pt-5">
          <div className="rounded bg-surface px-4 py-6 text-center">
            <span className="text-xs text-text-2">결제 금액</span>
            <strong className="mt-0.5 block text-[30px] font-bold tracking-tight">
              <Money value={amount} />
            </strong>
            <div className="mt-2 text-[13px] text-text-2">{orderName}</div>
          </div>
          <dl className="m-0 grid grid-cols-[76px_minmax(0,1fr)] gap-x-3 gap-y-2 text-[13px]">
            <dt className="text-text-2">주문명</dt>
            <dd className="m-0">{orderName}</dd>
            <dt className="text-text-2">주문번호</dt>
            <dd className="m-0 tabular-nums tracking-[.2px]">{orderNo}</dd>
          </dl>
          {MockOutcomePicker ? (
            <MockOutcomePicker value={choice.outcome} onChange={(outcome, prefixes) => setChoice({ outcome, prefixes })} />
          ) : null}
        </div>
        <div className="grid grid-cols-2 gap-2 px-6 pb-6 pt-5">
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={paying}>
            닫기
          </button>
          <button type="button" className="btn btn-primary" onClick={pay} disabled={paying}>
            결제
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
