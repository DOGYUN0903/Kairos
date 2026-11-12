"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useOrder, type OrderDetail } from "@/features/orders/api";
import { MockPaymentDialog } from "@/features/payment/components/MockPaymentDialog";
import { PanelDesc, PanelMeta, StatusPanel } from "@/features/payment/components/StatusPanel";
import { PG_PROVIDER, TOSS_CLIENT_KEY } from "@/features/payment/config";
import { rememberOrderRef } from "@/features/payment/order-ref-store";
import { canPay, orderNameOf } from "@/features/payment/pay-state";
import { requestTossPayment } from "@/features/payment/toss";
import { formatCountdown, secondsUntilKst } from "@/lib/datetime";
import { floorDiv, mod } from "@/lib/money";

const TOSS_KEY_MISSING = "토스 클라이언트 키가 설정되지 않았습니다";

function useNow(): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

/**
 * 결제 페이지. 주문서에서 넘어온 직후(?auto=1)와 결제 대기 주문의 "결제하기"가 모두 여기로 온다.
 * 이 마운트에서 새로 읽은 주문이 결제 대기·기한 내·결제 미진행일 때만 결제창을 열고, 아니면 주문 상세로 보낸다.
 */
export function PayPage({ orderId }: { orderId: number }) {
  const router = useRouter();
  const auto = useSearchParams().get("auto") === "1";
  const order = useOrder(orderId);
  const now = useNow();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [launching, setLaunching] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const launchingRef = useRef(false);
  const autoDone = useRef(false);

  // 캐시에 남은 예전 상태(결제 대기)로 결제창을 열지 않는다.
  const data = order.data;
  const fresh = data !== undefined && order.isFetchedAfterMount && !order.isError;
  const payable = fresh && canPay(data, now);
  const leave = fresh && !payable;

  useEffect(() => {
    if (leave) router.replace(`/my/orders/${orderId}`);
  }, [leave, orderId, router]);

  const openWindow = useCallback((o: OrderDetail) => {
    if (PG_PROVIDER === "mock") {
      rememberOrderRef(o.orderNo, o.orderId);
      setDialogOpen(true);
      return;
    }
    if (!TOSS_CLIENT_KEY || launchingRef.current) return;
    launchingRef.current = true;
    setLaunching(true);
    setPayError(null);
    rememberOrderRef(o.orderNo, o.orderId);
    requestTossPayment({ orderNo: o.orderNo, orderName: orderNameOf(o), amount: o.totalAmount, orderId: o.orderId })
      .catch((e: unknown) => setPayError(e instanceof Error && e.message ? e.message : "결제창을 열지 못했습니다."))
      .finally(() => {
        launchingRef.current = false;
        setLaunching(false);
      });
  }, []);

  useEffect(() => {
    if (!auto || autoDone.current || !payable || !data) return;
    autoDone.current = true;
    // 처음 한 번만 자동으로 연다: 새로고침·뒤로 가기에는 다시 열리지 않게 auto 를 주소에서 뺀다.
    window.history.replaceState(null, "", `/my/orders/${orderId}/pay`);
    openWindow(data);
  }, [auto, payable, data, orderId, openWindow]);

  if (order.isError) return <Shell><ErrorState error={order.error} onRetry={() => void order.refetch()} /></Shell>;
  if (!fresh || !payable) return <Shell><PageSkeleton /></Shell>;

  const remaining = secondsUntilKst(data.expiresAt, now);
  const orderName = orderNameOf(data);
  const keyMissing = PG_PROVIDER === "toss" && !TOSS_CLIENT_KEY;

  return (
    <Shell>
      <StatusPanel
        title="결제 대기"
        tone="wait"
        side={
          <>
            <div
              role="timer"
              aria-label={`결제까지 남은 시간 ${floorDiv(remaining, 60)}분 ${mod(remaining, 60)}초`}
              className="text-right"
            >
              <span className="block text-xs text-text-2">남은 시간</span>
              <strong className="text-[28px] font-bold leading-tight tracking-wide tabular-nums">{formatCountdown(remaining)}</strong>
            </div>
            <button type="button" className="btn btn-primary btn-lg" disabled={keyMissing || launching} onClick={() => openWindow(data)}>
              결제하기
            </button>
          </>
        }
      >
        <PanelDesc>남은 시간 안에 결제하지 않으면 주문이 자동으로 취소됩니다.</PanelDesc>
        <PanelMeta items={[{ label: "주문번호", value: data.orderNo }]} />
      </StatusPanel>

      {(keyMissing || payError) && (
        <p role="alert" className="mt-4 text-[13px] text-danger">
          {keyMissing ? TOSS_KEY_MISSING : payError}
        </p>
      )}

      <section aria-label="주문 요약" className="card mt-4 p-6">
        <h2 className="mb-4 text-base font-bold">주문 요약</h2>
        <dl className="grid grid-cols-[76px_minmax(0,1fr)] gap-x-3 gap-y-2.5 text-sm">
          <dt className="text-text-2">주문 상품</dt>
          <dd>{orderName}</dd>
          <dt className="text-text-2">주문번호</dt>
          <dd className="tabular-nums tracking-[.2px]">{data.orderNo}</dd>
        </dl>
        <div className="mt-4 flex items-baseline justify-between border-t border-border pt-4 font-bold">
          <span>결제 금액</span>
          <strong className="text-2xl tracking-tight">
            <Money value={data.totalAmount} />
          </strong>
        </div>
      </section>

      <div className="mt-5 flex justify-end">
        <Link className="btn btn-ghost" href={`/my/orders/${orderId}`}>
          주문 상세
        </Link>
      </div>

      {PG_PROVIDER === "mock" && (
        <MockPaymentDialog
          open={dialogOpen}
          onClose={() => setDialogOpen(false)}
          orderId={data.orderId}
          orderNo={data.orderNo}
          orderName={orderName}
          amount={data.totalAmount}
        />
      )}
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return <div className="container max-w-[880px] pb-16 pt-12">{children}</div>;
}
