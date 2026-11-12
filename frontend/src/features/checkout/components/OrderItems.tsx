import { Store } from "lucide-react";
import { Money } from "@/components/common/Money";
import type { OrderQuote, QuoteLine } from "@/features/checkout/api";
import type { BenefitSelection } from "@/features/checkout/benefit-selection";
import { CouponBadges } from "@/features/checkout/components/CouponBadges";
import { conditionLabel } from "@/features/coupons/discount-text";

type Props = {
  quote: OrderQuote;
  selection: BenefitSelection;
  /** 그 줄의 상품 쿠폰 선택 창을 연다. */
  onPickCoupon: (line: QuoteLine) => void;
  /** 새 견적을 기다리는 중이면 할인액을 보이지 않는다(옛 견적의 값이다). */
  requoting: boolean;
};

/** 판매자별로 묶는다. 순서는 견적 응답의 판매자 순서를 따르고, 금액은 모두 서버 값이다. */
export function OrderItems({ quote, selection, onPickCoupon, requoting }: Props) {
  // 안내 문구에 쓰는 개수(세기이며 금액이 아니다).
  const couponLines = quote.items.filter((l) => l.availableCoupons.some((c) => c.usable)).length;
  const cartUsable = quote.cartCoupons.filter((c) => c.usable).length;
  const nothingPicked = selection.cartCouponId === null && Object.keys(selection.productCoupons).length === 0;
  return (
    <section aria-labelledby="items-h">
      <div className="mb-3 flex items-end justify-between">
        <h2 id="items-h" className="text-lg font-bold">
          주문 상품 <span className="font-medium text-text-2">{quote.items.length}</span>
        </h2>
        <span className="text-xs text-text-2">판매자별로 따로 배송됩니다</span>
      </div>
      {nothingPicked && couponLines + cartUsable > 0 && (
        <div className="notice mt-4" role="status">
          <span>
            <strong>쓸 수 있는 쿠폰이 있습니다.</strong> 쿠폰을 쓸 수 있는 상품이 {couponLines}개, 장바구니 쿠폰이 {cartUsable}장 있습니다. 쿠폰은 자동으로 적용되지 않으니 직접 선택해 주세요.
          </span>
        </div>
      )}
      {quote.sellerQuotes.map((sq) => {
        const lines: QuoteLine[] = quote.items.filter((l) => l.sellerId === sq.sellerId);
        return (
          <div key={sq.sellerId} role="group" aria-label={`${sq.sellerName} 상품`} className="mt-4 rounded-lg border border-border">
            <div className="flex min-h-12 items-center gap-2 rounded-t-lg border-b border-border bg-surface px-5 text-sm font-bold">
              <Store className="h-4 w-4" aria-hidden />
              {sq.sellerName}
            </div>
            {lines.map((l) => (
              <div key={l.skuId} className="border-b border-border last:border-b-0">
                <div className="flex items-center gap-4 px-5 py-4">
                  {l.thumbnailUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element -- 외부 이미지 URL 을 그대로 쓴다
                    <img src={l.thumbnailUrl} alt="" className="h-[72px] w-[72px] flex-none rounded bg-surface-2 object-cover" />
                  ) : (
                    <div aria-hidden className="h-[72px] w-[72px] flex-none rounded bg-surface-2" />
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="text-xs text-text-2">{l.brandName}</div>
                    <div className="truncate font-medium">{l.productName}</div>
                    <div className="text-[13px] text-text-2">
                      {l.optionDisplayName && `${l.optionDisplayName} · `}
                      <Money value={l.unitPrice} />
                    </div>
                  </div>
                  <div className="w-20 text-right text-[13px] text-text-2">수량 {l.quantity}개</div>
                  <div className="w-28 text-right font-bold">
                    <Money value={l.lineAmount} />
                  </div>
                </div>
                <CouponLine line={l} selection={selection} requoting={requoting} onPick={() => onPickCoupon(l)} />
              </div>
            ))}
            <div className="flex items-center justify-end gap-5 border-t border-border px-5 py-3.5 text-[13px] text-text-2">
              <span>
                상품 금액{" "}
                <b className="ml-1.5 text-foreground">
                  <Money value={sq.itemsAmount} />
                </b>
              </span>
              <span aria-hidden className="h-3 w-px bg-border" />
              <span>
                배송비{" "}
                <b className="ml-1.5 text-foreground">
                  {sq.shippingFeePayer === "PLATFORM" ? "무료배송" : <Money value={sq.shippingFee} />}
                </b>
              </span>
            </div>
          </div>
        );
      })}
    </section>
  );
}

function CouponLine({ line: l, selection, onPick, requoting }: { line: QuoteLine; selection: BenefitSelection; onPick: () => void; requoting: boolean }) {
  if (l.availableCoupons.length === 0) return null;
  const pickedId = selection.productCoupons[l.skuId];
  const picked = pickedId === undefined ? undefined : l.availableCoupons.find((c) => c.couponId === pickedId);
  const usableCount = l.availableCoupons.filter((c) => c.usable).length;
  return (
    <div className="flex items-center justify-between gap-3 border-t border-dashed border-border bg-[#FAFBFC] py-3 pl-[108px] pr-5 text-[13px]">
      {pickedId !== undefined ? (
        <>
          <span className="flex min-w-0 items-center gap-2">
            {picked ? (
              <>
                <CouponBadges coupon={picked} scope="상품 쿠폰" />
                <b className="font-medium">{picked.name}</b>
                {conditionLabel(picked) && <span className="text-text-2">{conditionLabel(picked)}</span>}
              </>
            ) : (
              <span className="text-text-2">쿠폰 적용 중</span>
            )}
          </span>
          <span className="flex items-center gap-3">
            <span className="font-bold tabular-nums text-danger">
              {requoting ? "계산 중…" : <>−<Money value={l.productCouponDiscount} /></>}
            </span>
            <button type="button" className="min-h-9 text-[13px] text-text-2 underline underline-offset-[3px] hover:text-navy" onClick={onPick}>
              변경
            </button>
          </span>
        </>
      ) : (
        <>
          <span className="text-text-2">상품 쿠폰</span>
          <button type="button" className="btn btn-sm border-navy" onClick={onPick}>
            쿠폰 선택
            <span className="inline-flex h-[18px] items-center rounded-[9px] bg-navy px-[7px] text-[11px] font-bold text-white">{usableCount}장 사용 가능</span>
          </button>
        </>
      )}
    </div>
  );
}
