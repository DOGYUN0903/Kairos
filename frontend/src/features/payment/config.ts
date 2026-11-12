// 백엔드 KAIROS_PG_PROVIDER 와 같은 값이어야 한다(다르면 결제 승인이 실패한다).
export const PG_PROVIDER: "mock" | "toss" = process.env.NEXT_PUBLIC_PG_PROVIDER === "toss" ? "toss" : "mock";

/** 토스페이먼츠 클라이언트 키(공개용). 시크릿 키는 프론트에 두지 않는다. */
export const TOSS_CLIENT_KEY: string = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? "";
