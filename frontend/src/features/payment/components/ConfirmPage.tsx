"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/useAuth";
import { useOrder } from "@/features/orders/api";
import { usePollingWindow } from "@/features/orders/polling";
import { confirmPayment } from "@/features/payment/api";
import { PanelActions, PanelDesc, StatusPanel } from "@/features/payment/components/StatusPanel";
import { parseConfirmParams, toConfirmView, type ConfirmView } from "@/features/payment/confirm-state";
import { isPaid } from "@/features/payment/pay-state";
import { queryKeys } from "@/lib/query-keys";

const ORDERS = "/my/orders";

/**
 * 결제 승인 처리(/checkout/success). 모의 결제와 토스가 같은 쿼리로 들어온다.
 * 승인 요청은 마운트당 한 번만 나가고, 결과를 모르면 완료도 실패도 말하지 않는다.
 */
export function ConfirmPage() {
  const sp = useSearchParams();
  const { status } = useAuth();
  const qc = useQueryClient();
  const router = useRouter();
  const params = useMemo(() => parseConfirmParams(sp), [sp]);
  const [view, setView] = useState<ConfirmView | null>(null);
  const fired = useRef(false);

  useEffect(() => {
    // 토스에서 전체 페이지 이동으로 돌아오면 액세스 토큰이 없다. 인증 부트스트랩이 끝난 뒤에 보낸다.
    if (status !== "authenticated" || !params || fired.current) return;
    fired.current = true;
    // cleanup 에서 취소 플래그를 두지 않는다: StrictMode 의 이중 effect 에서 ref 가드와 겹치면 응답이 버려진다.
    void confirmPayment(params).then((result) => setView(toConfirmView(result)));
  }, [status, params]);

  const approvedOrderId = view?.kind === "approved" ? view.orderId : null;
  useEffect(() => {
    if (!view || view.kind === "pending") return;
    void qc.invalidateQueries({ queryKey: queryKeys.orders.root });
    if (approvedOrderId !== null) {
      void qc.invalidateQueries({ queryKey: queryKeys.cart });
      // 뒤로 가기로 승인 화면에 돌아오지 않게 교체 이동한다.
      router.replace(`${ORDERS}/${approvedOrderId}/complete`);
    }
  }, [view, approvedOrderId, qc, router]);

  return (
    <div className="container max-w-[880px] pb-16 pt-12">
      <Body params={params} view={view} />
    </div>
  );
}

function Body({ params, view }: { params: ReturnType<typeof parseConfirmParams>; view: ConfirmView | null }) {
  if (!params) {
    return (
      <div role="alert">
        <StatusPanel title="결제 정보를 확인할 수 없습니다" tone="muted">
          <PanelDesc>주소가 올바르지 않습니다. 주문 내역에서 주문 상태를 확인해 주세요.</PanelDesc>
        </StatusPanel>
        <PanelActions>
          <Link className="btn btn-primary" href={ORDERS}>
            주문 내역
          </Link>
        </PanelActions>
      </div>
    );
  }
  if (!view || view.kind === "approved") {
    return (
      <div role="status" aria-live="polite">
        <StatusPanel title="결제 처리 중" busy>
          <PanelDesc>결제를 처리하고 있습니다. 창을 닫거나 다시 결제하지 말고 잠시만 기다려 주세요.</PanelDesc>
        </StatusPanel>
      </div>
    );
  }
  if (view.kind === "pending") return <Pending orderId={params.orderId} orderNo={params.orderNo} />;
  if (view.kind === "rejected") return <Rejected />;
  return (
    <div role="alert">
      <StatusPanel title="결제를 처리하지 못했습니다" tone="muted">
        <PanelDesc>{view.message}</PanelDesc>
        <PanelDesc>주문의 실제 상태는 주문 상세에서 확인할 수 있습니다.</PanelDesc>
      </StatusPanel>
      <PanelActions>
        {params.orderId !== null ? (
          <Link className="btn btn-primary" href={`${ORDERS}/${params.orderId}`}>
            주문 상세
          </Link>
        ) : (
          <Link className="btn btn-primary" href={ORDERS}>
            주문 내역
          </Link>
        )}
      </PanelActions>
    </div>
  );
}

/** 주문이 취소된 뒤의 다음 단계. 장바구니 주문이면 상품이 장바구니에 남아 있고, 바로 구매면 주문 내역에서 확인한다. */
function CanceledActions() {
  return (
    <PanelActions>
      <Link className="btn btn-ghost" href={ORDERS}>
        주문 내역
      </Link>
      <Link className="btn btn-primary" href="/cart">
        장바구니로
      </Link>
    </PanelActions>
  );
}

/** PG 가 결제를 거절했다(402 PAYMENT_REJECTED). 서버가 주문을 취소하고 재고를 되돌렸다. */
function Rejected() {
  return (
    <div role="alert">
      <StatusPanel title="결제 거절" tone="muted">
        <PanelDesc>결제가 거절되어 주문이 취소되었습니다. 재고와 장바구니는 그대로입니다</PanelDesc>
      </StatusPanel>
      <CanceledActions />
    </div>
  );
}

/** 확인 중이던 주문이 취소됐다. 결제 기한 만료·응답 유실 뒤 정리일 수도 있어 "거절"이라고 말하지 않는다. */
function CanceledWhilePending() {
  return (
    <div role="alert">
      <StatusPanel title="결제 미완료" tone="muted">
        <PanelDesc>결제가 완료되지 않아 주문이 취소되었습니다</PanelDesc>
      </StatusPanel>
      <CanceledActions />
    </div>
  );
}

const PENDING_DESC = "결제 결과를 확인하고 있습니다. 잠시 후 자동으로 바뀌니 다시 결제하지 말고 기다려 주세요.";

function Pending({ orderId, orderNo }: { orderId: number | null; orderNo: string }) {
  if (orderId === null) return <PendingUnknownOrder />;
  return <PendingPoll orderId={orderId} orderNo={orderNo} />;
}

/** 주문을 조회할 수 없다(숫자 주문 ID 를 모르거나, 그 ID 로는 이 주문을 읽을 수 없다). 결과는 주문 내역에서 확인하게 한다. */
function PendingUnknownOrder() {
  return (
    <div role="status" aria-live="polite">
      <StatusPanel title="결제 확인 중" busy>
        <PanelDesc>결제 결과를 확인하고 있습니다. 다시 결제하지 말고 주문 내역에서 결과를 확인해 주세요.</PanelDesc>
      </StatusPanel>
      <PanelActions>
        <Link className="btn btn-primary" href={ORDERS}>
          주문 내역
        </Link>
      </PanelActions>
    </div>
  );
}

/**
 * 주문을 3초마다 다시 읽어(최대 2분) 승인되면 완료 화면으로, 취소되면 취소 안내로 바꾼다.
 * - 조회가 4xx 면(남의 주문·없는 oid) 다시 읽어도 같으므로 바로 멈추고 주문 내역으로 안내한다.
 * - 탭이 숨겨지면 주기 조회가 멈추고 2분 타이머만 흐른다. 그래서 2분이 끝나는 순간 한 번 더 읽고, 그 결과로 화면을 정한다.
 */
function PendingPoll({ orderId, orderNo }: { orderId: number; orderNo: string }) {
  const router = useRouter();
  const qc = useQueryClient();
  const [settled, setSettled] = useState(false);
  const poll = usePollingWindow(!settled);
  const order = useOrder(orderId, { refetchInterval: poll.interval });

  // oid 는 주소에서 온 값이다. 승인을 요청한 주문번호와 같은 주문일 때만 그 상태를 믿는다.
  const data = order.data;
  const mismatch = data !== undefined && data.orderNo !== orderNo;
  const unreadable = order.error instanceof ApiError && order.error.status >= 400 && order.error.status < 500;
  const paid = data !== undefined && !mismatch && isPaid(data);
  const canceled = data !== undefined && !mismatch && data.status === "CANCELED";

  useEffect(() => {
    if (mismatch || unreadable || paid || canceled) setSettled(true);
  }, [mismatch, unreadable, paid, canceled]);

  useEffect(() => {
    if (!paid) return;
    void qc.invalidateQueries({ queryKey: queryKeys.cart });
    void qc.invalidateQueries({ queryKey: queryKeys.orders.root });
    router.replace(`${ORDERS}/${orderId}/complete`);
  }, [paid, orderId, qc, router]);

  // 2분이 끝나면 마지막으로 한 번 더 읽는다. 끝나기 전에는 만료 안내를 띄우지 않는다.
  const [lastChecked, setLastChecked] = useState(false);
  const lastCheckFired = useRef(false);
  const refetch = order.refetch;
  useEffect(() => {
    if (!poll.expired || lastCheckFired.current) return;
    lastCheckFired.current = true;
    // cleanup 에서 취소하지 않는다(StrictMode 의 이중 effect 에서 ref 가드와 겹치면 결과가 버려진다).
    void refetch().finally(() => setLastChecked(true));
  }, [poll.expired, refetch]);

  if (mismatch || unreadable) return <PendingUnknownOrder />;
  if (canceled) return <CanceledWhilePending />;
  if (!paid && lastChecked) {
    return (
      <div role="status" aria-live="polite">
        <StatusPanel title="결제 확인 중">
          <PanelDesc>아직 결제 결과를 확인하지 못했습니다. 다시 결제하지 말고 주문 내역에서 결과를 확인해 주세요.</PanelDesc>
        </StatusPanel>
        <PanelActions>
          <Link className="btn btn-primary" href={`${ORDERS}/${orderId}`}>
            주문 상세
          </Link>
        </PanelActions>
      </div>
    );
  }
  return (
    <div role="status" aria-live="polite">
      <StatusPanel title="결제 확인 중" busy>
        <PanelDesc>{PENDING_DESC}</PanelDesc>
      </StatusPanel>
    </div>
  );
}
