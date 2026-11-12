import { useEffect, useState } from "react";
import type { OrderDetail } from "@/features/orders/api";

export const POLL_INTERVAL_MS = 3000;
/** 대사 작업이 60초 주기로 UNKNOWN 결제를 확정하므로 보통 이 안에 풀린다. */
export const POLL_LIMIT_MS = 120000;

/** 서버가 아직 결과를 확정하지 못한 주문인가: 결제가 진행 중·미확정이거나 취소·반품 환불 확인 중인 항목이 있다. */
export function needsPolling(order: OrderDetail): boolean {
  const payment = order.payment?.status;
  if (payment === "IN_PROGRESS" || payment === "UNKNOWN") return true;
  return order.sellerOrders.some((so) => so.items.some((item) => item.status === "CANCEL_REQUESTED" || item.status === "RETURN_REFUNDING"));
}

/** active 가 된 시점부터 2분 동안만 3초 간격을 준다. 2분이 지나면 expired 로 알리고 멈춘다. */
export function usePollingWindow(active: boolean): { interval: number | false; expired: boolean } {
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    if (!active) return;
    const timer = setTimeout(() => setExpired(true), POLL_LIMIT_MS);
    return () => {
      clearTimeout(timer);
      setExpired(false);
    };
  }, [active]);

  const live = active && !expired;
  return { interval: live ? POLL_INTERVAL_MS : false, expired: active && expired };
}
