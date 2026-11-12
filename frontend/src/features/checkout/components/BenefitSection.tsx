import { Money } from "@/components/common/Money";
import type { OrderQuote } from "@/features/checkout/api";
import type { BenefitSelection } from "@/features/checkout/benefit-selection";
import { CouponBadges } from "@/features/checkout/components/CouponBadges";
import { PointsField } from "@/features/checkout/components/PointsField";
import { conditionLabel } from "@/features/coupons/discount-text";
import { formatPoints } from "@/lib/money";

type Props = {
  quote: OrderQuote;
  selection: BenefitSelection;
  /** 보유 포인트(모르면 undefined). */
  balance: number | undefined;
  pointsRaw: string;
  onPointsRaw: (raw: string) => void;
  onCommitPoints: (points: number) => void;
  onPickCart: () => void;
};

/** 할인·포인트 영역. 모든 금액·한도는 견적 값이다. */
export function BenefitSection({ quote, selection, balance, pointsRaw, onPointsRaw, onCommitPoints, onPickCart }: Props) {
  const picked = selection.cartCouponId === null ? undefined : quote.cartCoupons.find((c) => c.couponId === selection.cartCouponId);
  const usableCount = quote.cartCoupons.filter((c) => c.usable).length;
  return (
    <section className="card" aria-labelledby="ben-h">
      <div className="flex items-center justify-between border-b border-border px-5 py-4">
        <h2 id="ben-h" className="text-lg font-bold">
          할인·포인트
        </h2>
        <span className="text-[13px] text-text-2">선택할 때마다 결제 금액이 다시 계산됩니다</span>
      </div>
      <div className="grid grid-cols-[120px_minmax(0,1fr)] items-start gap-4 px-5 py-[18px]">
        <div className="pt-2.5 font-bold">
          장바구니 쿠폰<small className="mt-0.5 block text-xs font-normal text-text-2">주문에 한 장</small>
        </div>
        <div className="flex min-h-[44px] items-center justify-between gap-3">
          {selection.cartCouponId !== null ? (
            <>
              <span className="flex min-w-0 items-center gap-2 text-[13px]">
                {picked ? (
                  <>
                    <CouponBadges coupon={picked} scope="장바구니 쿠폰" />
                    <b className="font-medium">{picked.name}</b>
                    {conditionLabel(picked) && <span className="text-text-2">{conditionLabel(picked)}</span>}
                  </>
                ) : (
                  <span className="text-text-2">쿠폰 적용 중</span>
                )}
              </span>
              <span className="flex items-center gap-3">
                <span className="font-bold tabular-nums text-danger">
                  −<Money value={quote.cartCouponDiscount} />
                </span>
                <button type="button" className="min-h-9 text-[13px] text-text-2 underline underline-offset-[3px] hover:text-navy" onClick={onPickCart}>
                  변경
                </button>
              </span>
            </>
          ) : quote.cartCoupons.length === 0 ? (
            <span className="text-[13px] text-text-2">사용 가능한 쿠폰이 없습니다</span>
          ) : (
            <>
              <span className="text-[13px] text-text-2">선택한 쿠폰이 없습니다</span>
              <button type="button" className="btn btn-sm border-navy" onClick={onPickCart}>
                쿠폰 선택
                <span className="inline-flex h-[18px] items-center rounded-[9px] bg-navy px-[7px] text-[11px] font-bold text-white">{usableCount}장 사용 가능</span>
              </button>
            </>
          )}
        </div>
      </div>
      <div className="grid grid-cols-[120px_minmax(0,1fr)] items-start gap-4 border-t border-border px-5 py-[18px]">
        <div className="pt-2.5 font-bold">
          포인트<small className="mt-0.5 block text-xs font-normal text-text-2">{balance === undefined ? "보유 포인트 확인 중" : `보유 ${formatPoints(balance)}`}</small>
        </div>
        <PointsField balance={balance} maxUsable={quote.maxUsablePoints} raw={pointsRaw} onRawChange={onPointsRaw} applied={selection.points} onCommit={onCommitPoints} />
      </div>
    </section>
  );
}
