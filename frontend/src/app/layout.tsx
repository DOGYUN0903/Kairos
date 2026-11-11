import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "KAIROS",
  description: "KAIROS 타임딜",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
