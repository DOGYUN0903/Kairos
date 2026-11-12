import { TOSS_CLIENT_KEY } from "@/features/payment/config";
import { failUrl, successUrl } from "@/features/payment/payment-urls";

/*
 * 토스페이먼츠 결제창. 2026-11-12 에 공식 문서와 SDK 타입 정의로 확인한 내용:
 * - 패키지: @tosspayments/tosspayments-sdk (v2, 2.10.0 고정). `loadTossPayments(clientKey)` → `tossPayments.payment({ customerKey })`
 *   → `payment.requestPayment({ method: "CARD", amount: { currency: "KRW", value }, orderId, orderName, successUrl, failUrl })`.
 *   (docs.tosspayments.com/sdk/v2/js/payment — 문서 이름은 "결제창(구버전)". "API 개별 연동 키"(test_ck_…/live_ck_…)를 쓴다.
 *   새 "결제창형"(widgets.renderPaymentWindow)은 결제위젯 키(test_gck_…)와 상점 관리자 설정이 필요해 쓰지 않는다.)
 * - successUrl·failUrl 을 주면 Redirect 방식이다. 성공: `{successUrl}?amount=&orderId=&paymentKey=`,
 *   실패: `{failUrl}?code=&message=&orderId=`(사용자가 결제를 중단한 PAY_PROCESS_CANCELED 는 orderId 가 없다).
 *   orderId 는 영문·숫자·`-_=` 6~64자 → 주문번호(21자 [A-Z0-9])가 그대로 맞는다. orderName 은 100자 이하.
 * - successUrl 의 기존 쿼리(oid) 보존: 문서(blog/what-is-successurl)가 "커스텀 파라미터는 지원하지 않으니 필요하면
 *   successUrl 에 쿼리 파라미터를 임의로 붙이라"며 `"/success?key={VALUE}"` 예시를 든다. 보존된다는 문장을 직접 적지는 않았고
 *   실제 결제로 확인하지 못했다. 보존되지 않아도 승인 화면이 sessionStorage 의 주문번호 → 주문 ID(order-ref-store)로 받친다.
 * - 비회원 결제는 customerKey 에 ANONYMOUS 를 넣는다. 회원 식별자를 PG 에 넘기지 않으려고 이 값을 쓴다.
 * - 시크릿 키를 넣으면 SDK 가 InsecureKeyUsageError 를 던진다. 프론트에는 공개용 클라이언트 키만 있다.
 *
 * SDK 는 여기서만 동적으로 불러온다(모의 결제 번들에 들어가지 않는다).
 */
export async function requestTossPayment(args: { orderNo: string; orderName: string; amount: number; orderId: number }): Promise<void> {
  if (!TOSS_CLIENT_KEY) throw new Error("토스 클라이언트 키가 설정되지 않았습니다");
  const { loadTossPayments, ANONYMOUS } = await import("@tosspayments/tosspayments-sdk");
  const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY);
  const payment = tossPayments.payment({ customerKey: ANONYMOUS });
  const origin = window.location.origin;
  await payment.requestPayment({
    method: "CARD",
    amount: { currency: "KRW", value: args.amount },
    orderId: args.orderNo,
    orderName: args.orderName.slice(0, 100),
    successUrl: origin + successUrl(args.orderId),
    failUrl: origin + failUrl(args.orderId),
  });
}
