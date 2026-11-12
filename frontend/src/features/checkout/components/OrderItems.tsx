import { Store } from "lucide-react";
import { Money } from "@/components/common/Money";
import type { OrderQuote, QuoteLine } from "@/features/checkout/api";

/** 판매자별로 묶는다. 순서는 견적 응답의 판매자 순서를 따르고, 금액은 모두 서버 값이다. */
export function OrderItems({ quote }: { quote: OrderQuote }) {
  return (
    <section aria-labelledby="items-h">
      <div className="mb-3 flex items-end justify-between">
        <h2 id="items-h" className="text-lg font-bold">
          주문 상품 <span className="font-medium text-text-2">{quote.items.length}</span>
        </h2>
        <span className="text-xs text-text-2">판매자별로 따로 배송됩니다</span>
      </div>
      {quote.sellerQuotes.map((sq) => {
        const lines: QuoteLine[] = quote.items.filter((l) => l.sellerId === sq.sellerId);
        return (
          <div key={sq.sellerId} role="group" aria-label={`${sq.sellerName} 상품`} className="mt-4 rounded-lg border border-border">
            <div className="flex min-h-12 items-center gap-2 rounded-t-lg border-b border-border bg-surface px-5 text-sm font-bold">
              <Store className="h-4 w-4" aria-hidden />
              {sq.sellerName}
            </div>
            {lines.map((l) => (
              <div key={l.skuId} className="flex items-center gap-4 border-b border-border px-5 py-4 last:border-b-0">
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
                    {l.optionDisplayName} · <Money value={l.unitPrice} />
                  </div>
                </div>
                <div className="w-20 text-right text-[13px] text-text-2">수량 {l.quantity}개</div>
                <div className="w-28 text-right font-bold">
                  <Money value={l.lineAmount} />
                </div>
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
