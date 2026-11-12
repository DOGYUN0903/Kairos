"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { PanelActions, PanelDesc, StatusPanel } from "@/features/payment/components/StatusPanel";
import { recallOrderId } from "@/features/payment/order-ref-store";
import { parseId } from "@/lib/ids";

/** 토스가 "사용자가 결제를 취소했다"고 알리는 코드(근거는 features/payment/toss.ts 주석). */
const USER_CANCEL_CODES: readonly string[] = ["PAY_PROCESS_CANCELED", "USER_CANCEL"];

/**
 * 주소의 code 로 고정 문구를 고른다. 주소의 message 는 누구나 넣을 수 있는 값이라 화면에 옮기지 않는다
 * (우리 화면에서 임의 안내문을 보여 주는 피싱에 쓰일 수 있다).
 */
export function failMessage(code: string | null): string {
  return code !== null && USER_CANCEL_CODES.includes(code) ? "결제를 취소했습니다" : "결제가 완료되지 않았습니다";
}

/** 결제창 실패·사용자 취소(/checkout/fail, 토스 전용). 승인을 호출하지 않았으므로 주문은 결제 대기로 남아 있다. */
export function FailPage() {
  const sp = useSearchParams();
  const orderNo = sp.get("orderId");
  const orderId = parseId(sp.get("oid")) ?? (orderNo ? recallOrderId(orderNo) : null);

  return (
    <div className="container max-w-[880px] pb-16 pt-12">
      <StatusPanel title="결제 미완료" tone="muted">
        <PanelDesc>{failMessage(sp.get("code"))}</PanelDesc>
        <PanelDesc>주문은 결제 대기로 남아 있습니다. 결제 기한 안에 다시 결제할 수 있습니다.</PanelDesc>
      </StatusPanel>
      <PanelActions>
        {orderId !== null ? (
          <>
            <Link className="btn btn-ghost" href={`/my/orders/${orderId}`}>
              주문 상세
            </Link>
            <Link className="btn btn-primary" href={`/my/orders/${orderId}/pay`}>
              다시 결제
            </Link>
          </>
        ) : (
          <Link className="btn btn-primary" href="/my/orders">
            주문 내역
          </Link>
        )}
      </PanelActions>
    </div>
  );
}
