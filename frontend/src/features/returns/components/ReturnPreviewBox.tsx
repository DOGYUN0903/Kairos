"use client";

import { resolveError } from "@/api/errors";
import { RefundBreakdown } from "@/features/returns/components/RefundBreakdown";
import { previewBlocksRequest, type ReturnPreview, type ReturnReason } from "@/features/returns/api";
import { feeBearerNotice } from "@/features/returns/return-text";
import { refundRows } from "@/features/returns/return-actions";

type PreviewState = { isError: boolean; isSuccess: boolean; error: unknown; data: ReturnPreview | undefined };

export const ESTIMATE_NOTE = "실제 환불 금액은 판매자가 상품을 받은 뒤 확정됩니다. 같은 주문의 다른 상품을 취소·반품하면 달라질 수 있습니다";

/**
 * 반품 환불 미리보기 영역. 금액은 모두 서버 값이고 항상 "예상"으로 보인다.
 * 서버가 자격이 없다고 답한 경우(409)는 메시지를 보이고, 그 밖의 실패는 안내만 하며 요청을 막지 않는다.
 */
export function ReturnPreviewBox({ reason, preview }: { reason: ReturnReason | null; preview: PreviewState }) {
  let body;
  if (reason == null) {
    body = <p className="text-[13px] text-text-2">사유를 선택하면 환불 예정 금액을 보여 드립니다</p>;
  } else if (preview.isError) {
    body = previewBlocksRequest(preview.error) ? (
      <div role="alert" className="notice notice-error">
        <span>{resolveError(preview.error).message}</span>
      </div>
    ) : (
      <div role="alert" className="notice notice-warn">
        <span>환불 예정 금액을 불러오지 못했습니다. 반품 요청은 그대로 할 수 있습니다.</span>
      </div>
    );
  } else if (!preview.isSuccess || !preview.data) {
    body = (
      <p className="text-[13px] text-text-2" aria-busy="true">
        환불 예정 금액을 확인하고 있습니다…
      </p>
    );
  } else {
    const p = preview.data;
    body = (
      <>
        <RefundBreakdown rows={refundRows(p, "환불 예정 금액")} title="환불 예정 금액" />
        <ul className="flex flex-col gap-1 text-[13px] text-text-2">
          <li>{feeBearerNotice(p.feeBearer)}</li>
          {p.outboundFeeRecovered > 0 && <li>무료배송으로 받은 상품이라 발송 배송비가 함께 차감됩니다</li>}
          {p.productCouponReturnable && <li>사용한 상품 쿠폰은 유효기간이 남아 있으면 돌아옵니다</li>}
        </ul>
      </>
    );
  }
  return (
    <div className="flex flex-col gap-2">
      {body}
      <p className="text-xs text-text-2">{ESTIMATE_NOTE}</p>
    </div>
  );
}
