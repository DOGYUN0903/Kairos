import { Money } from "@/components/common/Money";
import { formatPoints } from "@/lib/money";
import type { OrderQuote } from "@/features/checkout/api";
import { payButtonLabel } from "@/features/checkout/checkout-guard";

type Props = { quote: OrderQuote; payable: boolean; submitting: boolean };

/** 모든 금액은 서버 견적 값이다(합계·곱셈을 하지 않는다). */
export function CheckoutSummary({ quote, payable, submitting }: Props) {
  return (
    <div className="rounded-lg border border-border bg-bg p-6" aria-busy={!payable}>
      <h2 className="mb-4 text-base font-bold">결제 금액</h2>
      <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-2.5 text-sm">
        <dt className="text-text-2">상품 금액</dt>
        <dd className="text-right font-medium tabular-nums">
          <Money value={quote.itemsAmount} />
        </dd>
        {quote.productCouponDiscount !== 0 && (
          <>
            <dt className="text-text-2">상품 쿠폰 할인</dt>
            <dd className="text-right font-medium tabular-nums text-danger">
              −<Money value={quote.productCouponDiscount} />
            </dd>
          </>
        )}
        {quote.cartCouponDiscount !== 0 && (
          <>
            <dt className="text-text-2">장바구니 쿠폰 할인</dt>
            <dd className="text-right font-medium tabular-nums text-danger">
              −<Money value={quote.cartCouponDiscount} />
            </dd>
          </>
        )}
        {quote.pointsUsed !== 0 && (
          <>
            <dt className="text-text-2">포인트 사용</dt>
            <dd className="text-right font-medium tabular-nums text-danger">−{formatPoints(quote.pointsUsed)}</dd>
          </>
        )}
        <dt className="text-text-2">배송비</dt>
        <dd className="text-right font-medium tabular-nums">
          <Money value={quote.buyerShippingFee} />
        </dd>
        {quote.sellerQuotes.map((s) => (
          <div key={s.sellerId} className="col-span-2 -mt-1.5 flex justify-between pl-2.5 text-xs text-text-3">
            <span>{s.sellerName}</span>
            <span className="tabular-nums">{s.shippingFeePayer === "PLATFORM" ? "무료배송" : <Money value={s.shippingFee} />}</span>
          </div>
        ))}
      </dl>
      <div className="mt-4 flex items-baseline justify-between border-t border-border pt-4 font-bold">
        <span>결제 금액</span>
        <strong className="text-2xl tracking-tight">
          <Money value={quote.totalAmount} />
        </strong>
      </div>
      <button type="submit" className="btn btn-primary btn-lg btn-block mt-5" disabled={!payable || submitting}>
        {submitting ? "주문 접수 중…" : payButtonLabel(quote.totalAmount)}
      </button>
      <span className="mt-2.5 block text-center text-xs text-text-2">{quote.totalAmount === 0 ? "결제 금액이 0원이라 결제창 없이 바로 주문이 완료됩니다" : "결제하기를 누르면 주문이 만들어지고 결제창이 열립니다"}</span>
    </div>
  );
}
