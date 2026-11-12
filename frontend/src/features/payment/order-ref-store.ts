// 주문번호 → 숫자 주문 ID. 결제창이 성공 주소의 oid 를 보존하지 않을 때의 대비다.
// 토큰이 아닌 값만 sessionStorage 에 둔다. storage 가 막혀 있으면 조용히 포기한다(승인 화면이 "주문 내역" 링크로 안내한다).
import { parseId } from "@/lib/ids";

const PREFIX = "kairos:order:";

export function rememberOrderRef(orderNo: string, orderId: number): void {
  try {
    sessionStorage.setItem(PREFIX + orderNo, String(orderId));
  } catch {
    // 저장하지 못해도 결제는 진행한다.
  }
}

export function recallOrderId(orderNo: string): number | null {
  try {
    return parseId(sessionStorage.getItem(PREFIX + orderNo));
  } catch {
    return null;
  }
}
