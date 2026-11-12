"use client";

import Link from "next/link";
import { PanelDesc, PanelMeta, StatusPanel } from "@/features/payment/components/StatusPanel";
import { canPay, orderHeader } from "@/features/orders/actions";
import type { OrderDetail } from "@/features/orders/api";
import { PayCountdown } from "@/features/orders/components/PayCountdown";
import { formatDateTime } from "@/lib/datetime";

/**
 * 주문 상세의 상태 헤더. aria-live 로 상태 변화(결제 확인 중 → 결제 완료 등)를 알린다.
 * pollExpired 는 자동 재조회가 2분 안에 끝나지 않아 멈춘 상태다.
 */
export function OrderStatusHeader({
  order,
  nowMs,
  pollExpired,
  onRefresh,
}: {
  order: OrderDetail;
  nowMs: number;
  pollExpired: boolean;
  onRefresh: () => void;
}) {
  const kind = orderHeader(order, nowMs);
  const meta = [
    { label: "주문번호", value: order.orderNo },
    { label: "주문 일시", value: formatDateTime(order.createdAt) },
    ...(order.paidAt ? [{ label: "결제 일시", value: formatDateTime(order.paidAt) }] : []),
  ];

  let title: string;
  let desc: string;
  let side: React.ReactNode = undefined;
  let tone: "default" | "wait" | "muted" = "default";
  if (kind === "pending") {
    tone = "wait";
    title = "결제 대기";
    if (canPay(order, nowMs)) {
      desc = "남은 시간 안에 결제하지 않으면 주문이 자동으로 취소됩니다.";
      side = (
        <>
          <PayCountdown expiresAt={order.expiresAt} nowMs={nowMs} />
          <Link className="btn btn-primary btn-lg" href={`/my/orders/${order.orderId}/pay`}>
            결제하기
          </Link>
        </>
      );
    } else {
      desc = "결제 기한이 지났습니다. 주문이 곧 자동으로 취소됩니다.";
    }
  } else if (kind === "checking") {
    title = "결제 확인 중";
    desc = "결제 결과를 확인하고 있습니다. 잠시 후 자동으로 바뀌니 다시 결제하지 말고 기다려 주세요.";
  } else if (kind === "completed") {
    title = "구매 확정";
    desc = "구매가 확정된 주문입니다.";
  } else if (kind === "canceled") {
    tone = "muted";
    title = "취소";
    desc = "취소된 주문입니다. 결제된 금액이 있었다면 아래 결제 요약의 환불 금액에서 확인할 수 있습니다.";
  } else {
    title = "결제 완료";
    desc = "판매자가 상품을 준비하고 있습니다. 배송 상태는 판매자별로 아래에서 확인할 수 있습니다.";
  }

  return (
    <div aria-live="polite">
      <StatusPanel title={title} tone={tone} busy={kind === "checking"} heading="h2" side={side}>
        <PanelDesc>{desc}</PanelDesc>
        <PanelMeta items={meta} />
      </StatusPanel>
      {pollExpired && (
        <p role="status" className="mt-3 flex items-center gap-3 text-[13px] text-text-2">
          자동 확인이 끝났습니다. 새로고침해 결과를 확인해 주세요.
          <button type="button" className="btn btn-ghost btn-sm" onClick={onRefresh}>
            새로고침
          </button>
        </p>
      )}
    </div>
  );
}
