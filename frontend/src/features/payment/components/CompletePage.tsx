"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useOrder } from "@/features/orders/api";
import { PanelActions, PanelDesc, PanelMeta, StatusPanel } from "@/features/payment/components/StatusPanel";
import { isPaid } from "@/features/payment/pay-state";

/** 주문 완료. 이 마운트에서 새로 읽은 주문이 결제 완료(또는 구매 확정)일 때만 보여주고, 아니면 주문 상세로 보낸다. */
export function CompletePage({ orderId }: { orderId: number }) {
  const router = useRouter();
  const order = useOrder(orderId);
  const data = order.data;
  // 결제 페이지에서 캐시된 "결제 대기"를 보고 돌려보내지 않게 새로 읽은 값만 쓴다.
  const fresh = data !== undefined && order.isFetchedAfterMount && !order.isError;
  const paid = fresh && isPaid(data);
  const leave = fresh && !paid;

  useEffect(() => {
    if (leave) router.replace(`/my/orders/${orderId}`);
  }, [leave, orderId, router]);

  return (
    <div className="container max-w-[880px] pb-16">
      <div className="mb-6 mt-9 flex items-end justify-between gap-4">
        <h1 className="text-[26px] font-bold tracking-tight">주문 완료</h1>
        <ol aria-label="구매 단계" className="flex items-center gap-2.5 text-[13px] text-text-3">
          <li>장바구니</li>
          <li aria-hidden>›</li>
          <li>주문서</li>
          <li aria-hidden>›</li>
          <li aria-current="step" className="font-bold text-navy">
            주문 완료
          </li>
        </ol>
      </div>
      {order.isError ? (
        <ErrorState error={order.error} onRetry={() => void order.refetch()} />
      ) : !fresh || !paid ? (
        <PageSkeleton />
      ) : (
        <>
          <StatusPanel title="결제 완료" heading="h2">
            <PanelDesc>주문이 접수되었습니다. 판매자가 상품을 준비하고 있습니다.</PanelDesc>
            <PanelMeta items={[{ label: "주문번호", value: data.orderNo }]} />
          </StatusPanel>

          <section aria-label="결제 금액" className="card mt-4 flex items-baseline justify-between p-6 font-bold">
            <span>결제 금액</span>
            <strong className="text-2xl tracking-tight">
              <Money value={data.totalAmount} />
            </strong>
          </section>

          <section aria-label="배송지" className="card mt-4 p-6">
            <h2 className="mb-4 text-base font-bold">배송지</h2>
            <dl className="grid grid-cols-[76px_minmax(0,1fr)] gap-x-3 gap-y-2.5 text-sm">
              <dt className="text-text-2">받는 분</dt>
              <dd>{data.shippingAddress.receiverName}</dd>
              <dt className="text-text-2">연락처</dt>
              <dd className="tabular-nums">{data.shippingAddress.phone}</dd>
              <dt className="text-text-2">주소</dt>
              <dd>
                ({data.shippingAddress.zipCode}) {data.shippingAddress.address1} {data.shippingAddress.address2}
              </dd>
              {data.shippingAddress.memo ? (
                <>
                  <dt className="text-text-2">배송 메모</dt>
                  <dd>{data.shippingAddress.memo}</dd>
                </>
              ) : null}
            </dl>
          </section>

          <PanelActions>
            <Link className="btn btn-ghost" href="/">
              쇼핑 계속하기
            </Link>
            <Link className="btn btn-primary" href={`/my/orders/${orderId}`}>
              주문 상세 보기
            </Link>
          </PanelActions>
        </>
      )}
    </div>
  );
}
