// 백엔드 KAIROS_PG_PROVIDER 와 반드시 같은 값이어야 한다. 다르면:
// - 프론트 mock + 백엔드 toss: 모의 paymentKey 를 토스가 모르므로 승인이 거절되고 주문이 취소된다.
// - 프론트 toss + 백엔드 mock: Mock PG 는 접두사 없는 paymentKey 를 전부 승인하므로, 토스에서 실제 승인 없이 주문이 결제 완료가 된다
//   (백엔드가 mock 인 로컬에서만 가능한 조합이다).
export const PG_PROVIDER: "mock" | "toss" = process.env.NEXT_PUBLIC_PG_PROVIDER === "toss" ? "toss" : "mock";

/** 토스페이먼츠 클라이언트 키(공개용). 시크릿 키는 프론트에 두지 않는다. */
export const TOSS_CLIENT_KEY: string = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? "";
