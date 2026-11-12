"use client";

import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import { discountLabel } from "@/features/coupons/discount-text";
import { policyDisplayStatus, quantityLabel, targetLabel, type CouponPolicyResult } from "@/features/coupon-policies/policy-status";
import { useNow } from "@/hooks/useNow";
import { formatDate } from "@/lib/datetime";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

/** 판매자·관리자 공용 쿠폰 정책 표. 종료 버튼은 진행 중(ACTIVE)인 정책에만 있다("기간 종료"여도 서버 상태가 ACTIVE 면 종료할 수 있다). */
export function PolicyTable({ rows, onEnd, endingId, showType = false }: { rows: CouponPolicyResult[]; onEnd: (policyId: number) => void; endingId: number | null; showType?: boolean }) {
  const nowMs = useNow(false);
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th scope="col" className={TH}>이름</th>
            {showType && (
              <th scope="col" className={TH}>종류</th>
            )}
            <th scope="col" className={TH}>대상</th>
            <th scope="col" className={TH}>할인</th>
            <th scope="col" className={`${TH} text-right`}>최소 주문 금액</th>
            <th scope="col" className={`${TH} text-right`}>발급 / 총 수량</th>
            <th scope="col" className={TH}>발급 기간</th>
            <th scope="col" className={TH}>상태</th>
            <th scope="col" className={TH}>
              <span className="sr-only">작업</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((p) => {
            const status = policyDisplayStatus(p, nowMs);
            const ending = endingId === p.policyId;
            return (
              <tr key={p.policyId}>
                <td className={`${TD} font-medium`}>{p.name}</td>
                {showType && <td className={TD}>{p.couponType === "CART" ? "장바구니 쿠폰" : "상품 쿠폰"}</td>}
                <td className={TD}>
                  {p.targetType === "PRODUCT" ? (
                    <>
                      <span>상품 하나</span>
                      <div className="text-xs text-text-2">{targetLabel(p)}</div>
                    </>
                  ) : (
                    targetLabel(p)
                  )}
                </td>
                <td className={TD}>{discountLabel(p)}</td>
                <td className={`${TD} text-right tabular-nums`}>{p.minOrderAmount ? <Money value={p.minOrderAmount} /> : "없음"}</td>
                <td className={`${TD} text-right tabular-nums`}>{quantityLabel(p.issuedCount, p.totalQuantity)}</td>
                <td className={`${TD} whitespace-nowrap text-xs tabular-nums text-text-2`}>
                  {formatDate(p.validFrom)} ~ {formatDate(p.validUntil)}
                </td>
                <td className={TD}>
                  <StatusChip kind="couponPolicy" status={status} />
                </td>
                <td className={TD}>
                  {p.status === "ACTIVE" && (
                    <button type="button" className="btn btn-ghost btn-sm" disabled={ending} aria-label={`${p.name} 종료`} onClick={() => onEnd(p.policyId)}>
                      종료
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
