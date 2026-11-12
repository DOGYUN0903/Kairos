import Link from "next/link";
import type { ReactNode } from "react";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { RefundBreakdown } from "@/features/returns/components/RefundBreakdown";
import { ReturnSteps } from "@/features/returns/components/ReturnSteps";
import { refundRows } from "@/features/returns/return-actions";
import { returnReasonLabel } from "@/features/returns/return-text";
import { formatDate } from "@/lib/datetime";
import { joinDot } from "@/lib/join-dot";
import { formatKrw } from "@/lib/money";

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="card">
      <h2 className="border-b border-border px-5 py-3 text-[15px] font-bold">{title}</h2>
      <div className="p-5">{children}</div>
    </section>
  );
}

const NO_REFUND: Record<string, string> = {
  REJECTED: "거절된 반품이라 환불이 없습니다.",
  WITHDRAWN: "철회된 반품이라 환불이 없습니다.",
  CANCELED_BY_ADMIN: "중단된 반품이라 환불이 없습니다.",
};

/**
 * 반품 상세 본문(판매자·관리자 공용). 처리 버튼·진행 중 안내는 actions 로 받는다.
 * 금액은 전부 서버 값이다. orderHref 가 없으면 주문번호를 링크 없이 보인다.
 */
export function ReturnDetailView({ data: r, orderHref, actions, showSeller = false }: { data: ReturnRequestResponse; orderHref?: string; actions: ReactNode; showSeller?: boolean }) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="flex items-center gap-3 text-xl font-bold">
          반품 상세
          <StatusChip kind="return" status={r.status} />
        </h2>
        <dl className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-[13px] text-text-2">
          <div className="flex gap-1.5">
            <dt>요청일</dt>
            <dd className="font-medium tabular-nums text-foreground">{formatDate(r.requestedAt)}</dd>
          </div>
          <div className="flex gap-1.5">
            <dt>주문번호</dt>
            <dd className="font-medium tabular-nums text-foreground">{r.orderNo}</dd>
          </div>
        </dl>
      </div>

      <section className="card p-5" aria-label="진행 단계">
        <ReturnSteps r={r} />
      </section>

      <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6">
        <div className="flex flex-col gap-4">
          <section className="card flex items-center gap-4 p-5">
            <OrderThumb url={r.thumbnailUrl} size={72} />
            <div className="min-w-0 flex-1">
              {showSeller && <div className="text-xs text-text-2">{r.sellerName}</div>}
              <div className="font-medium">{r.productName}</div>
              <div className="mt-0.5 text-[13px] text-text-2">{joinDot(r.optionDisplayName, `${r.quantity}개`)}</div>
            </div>
            <div className="font-bold">
              <Money value={r.paidAmount} />
            </div>
          </section>

          <Section title="반품 사유">
            <dl className="grid grid-cols-[96px_1fr] gap-y-2 text-sm">
              <dt className="text-text-2">사유</dt>
              <dd>{returnReasonLabel(r.reason)}</dd>
              <dt className="text-text-2">부담 주체</dt>
              <dd>{r.feeBearer === "BUYER" ? "구매자" : "판매자"}</dd>
              {/* 구매자가 적은 상세 내용 자리: 응답에 `detail` 이 추가되면 여기에 "상세 내용" 줄을 더한다. */}
            </dl>
            {r.status === "REJECTED" && r.rejectReason && (
              <div role="alert" className="mt-4 rounded-lg border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm">
                <strong>거절 사유</strong>
                <br />
                {r.rejectReason}
              </div>
            )}
          </Section>

          <Section title="주문 정보">
            <dl className="grid grid-cols-[96px_1fr] gap-y-2 text-sm">
              <dt className="text-text-2">주문번호</dt>
              <dd className="tabular-nums">
                {orderHref ? (
                  <Link href={orderHref} className="underline">
                    {r.orderNo}
                  </Link>
                ) : (
                  r.orderNo
                )}
              </dd>
            </dl>
          </Section>

          <Section title="환불 내역">
            {r.status === "COMPLETED" || r.status === "REFUNDING" ? (
              <RefundBreakdown rows={refundRows(r, "환불 금액")} title="환불 금액" />
            ) : NO_REFUND[r.status] ? (
              <p className="text-sm text-text-2">{NO_REFUND[r.status]}</p>
            ) : (
              <p className="text-sm text-text-2">환불 금액은 판매자가 상품을 받은 뒤 확정됩니다.</p>
            )}
            {r.sellerBorneFee > 0 && (
              <p className="mt-3 text-sm font-medium">판매자 부담 반품비 {formatKrw(r.sellerBorneFee)} — 정산에서 차감됩니다</p>
            )}
          </Section>
        </div>

        <aside>
          <Section title="처리">{actions}</Section>
        </aside>
      </div>
    </div>
  );
}
