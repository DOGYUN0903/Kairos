// 데모 전용 계정. 운영 빌드에서는 DemoBar 와 함께 번들에서 제거된다(pnpm check:bundle).
export const DEMO_ACCOUNTS = {
  buyer: "buyer@kairos.demo",
  seller: "seller@kairos.demo",
  admin: "admin@kairos.demo",
} as const;

export const DEMO_PASSWORD = process.env.NEXT_PUBLIC_DEMO_PASSWORD ?? "";
