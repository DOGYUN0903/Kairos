import { Info } from "lucide-react";

/** 판매자·관리자 취소의 환불 안내. 구매자 귀책이 아니므로 배송비를 차감하지 않는다(금액은 서버가 정한다). */
export function NoDeductionNotice() {
  return (
    <div className="notice">
      <Info className="h-4 w-4 flex-none text-navy" aria-hidden />
      <span>
        <strong>배송비 차감 없이 환불됩니다.</strong> 취소하면 되돌릴 수 없습니다.
      </span>
    </div>
  );
}
