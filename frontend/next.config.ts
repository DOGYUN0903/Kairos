import type { NextConfig } from "next";

const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  // 같은 디렉터리에서 dev 서버를 둘 띄울 때(E2E 전용 서버 등) 빌드 디렉터리가 겹치지 않게 한다.
  distDir: process.env.NEXT_DIST_DIR || ".next",
  // 미설정이면 Next 가 인라인하지 않아 DemoBar 분기가 상수 폴딩되지 않는다. 항상 값을 정의한다.
  env: { NEXT_PUBLIC_DEMO_MODE: process.env.NEXT_PUBLIC_DEMO_MODE === "true" ? "true" : "false" },
  async rewrites() {
    return [
      { source: "/api/v1/:path*", destination: `${backendUrl}/api/v1/:path*` },
      { source: "/files/:path*", destination: `${backendUrl}/files/:path*` },
    ];
  },
};

export default nextConfig;
