import { defineConfig, devices } from "@playwright/test";

/**
 * C1 핵심 흐름 E2E. 실제 백엔드(`local,demo` 프로필)가 떠 있어야 한다.
 * - E2E_BASE_URL: 프론트 주소(기본 http://localhost:3000). 비어 있는 포트면 dev 서버를 직접 띄운다.
 * - BACKEND_URL: 백엔드 주소(기본 http://localhost:8080). 픽스처가 API 를 직접 부르고, 직접 띄우는 dev 서버도 이 주소로 프록시한다.
 * - KAIROS_DEMO_PASSWORD: 데모 관리자(admin@kairos.demo) 비밀번호. 없으면 관리자가 필요한 spec 은 건너뛴다.
 */
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3000";
const port = new URL(baseURL).port || "3000";

export default defineConfig({
  testDir: "e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 10_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    viewport: { width: 1440, height: 900 },
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } }],
  webServer: {
    command: `pnpm exec next dev --port ${port}`,
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120_000,
    env: {
      NEXT_PUBLIC_DEMO_MODE: "false",
      BACKEND_URL: process.env.BACKEND_URL ?? "http://localhost:8080",
      // 같은 디렉터리에서 다른 dev 서버가 돌고 있어도 .next 가 겹치지 않게 한다.
      NEXT_DIST_DIR: ".next-e2e",
    },
  },
});
