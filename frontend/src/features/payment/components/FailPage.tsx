"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { PanelActions, PanelDesc, StatusPanel } from "@/features/payment/components/StatusPanel";
import { recallOrderId } from "@/features/payment/order-ref-store";
import { parseId } from "@/lib/ids";

const DEFAULT_MESSAGE = "결제가 완료되지 않았습니다";
const MESSAGE_LIMIT = 200;

/** 결제창 실패·사용자 취소(/checkout/fail, 토스 전용). 승인을 호출하지 않았으므로 주문은 결제 대기로 남아 있다. */
export function FailPage() {
  const sp = useSearchParams();
  // message 는 주소에서 온 값이다. 글자 수를 제한하고 텍스트로만 보여준다.
  const message = sp.get("message")?.trim().slice(0, MESSAGE_LIMIT) || DEFAULT_MESSAGE;
  const orderNo = sp.get("orderId");
  const orderId = parseId(sp.get("oid")) ?? (orderNo ? recallOrderId(orderNo) : null);

  return (
    <div className="container max-w-[880px] pb-16 pt-12">
      <StatusPanel title="결제 미완료" tone="muted">
        <PanelDesc>{message}</PanelDesc>
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
