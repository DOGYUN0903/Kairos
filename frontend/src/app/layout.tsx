import type { Metadata } from "next";
import dynamic from "next/dynamic";
import { Providers } from "@/app/providers";
import "./globals.css";

// 리터럴 비교식이어야 Next 가 운영 빌드에서 분기와 import() 청크를 함께 제거한다(pnpm check:bundle 이 검증).
const DemoBar =
  process.env.NEXT_PUBLIC_DEMO_MODE === "true" ? dynamic(() => import("@/features/demo/DemoBar")) : null;

export const metadata: Metadata = {
  title: "KAIROS",
  description: "KAIROS 타임딜",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        <Providers>
          {children}
          {DemoBar ? <DemoBar /> : null}
        </Providers>
      </body>
    </html>
  );
}
