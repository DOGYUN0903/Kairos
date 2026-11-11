import { defineConfig, devices } from "@playwright/test";

/**
 * C1 핵심 흐름 E2E. 실제 백엔드(`local,demo` 프로필)가 떠 있어야 한다.
 * - E2E_BASE_URL: 프론트 주소(기본 http://localhost:3100 — Grafana 의 3000 과 겹치지 않게). 그 주소에 서버가 없으면
 *   scripts/e2e-server.sh 로 .next-e2e 에 빌드해 next start 로 띄운다. 이미 떠 있으면 그 서버를 그대로 쓰므로(reuseExistingServer),
 *   globalSetup 이 KAIROS 화면인지 먼저 확인한다.
 * - BACKEND_URL: 백엔드 주소(기본 http://localhost:8080). 픽스처가 API 를 직접 부르고, 직접 띄우는 dev 서버도 이 주소로 프록시한다.
 * - KAIROS_DEMO_PASSWORD: 데모 관리자(admin@kairos.demo) 비밀번호. 없으면 관리자가 필요한 spec 은 건너뛴다.
 */
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3100";
const port = new URL(baseURL).port || "80";

export default defineConfig({
  testDir: "e2e",
  globalSetup: "./e2e/global-setup.ts",
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
    command: `sh scripts/e2e-server.sh ${port}`,
    url: baseURL,
    reuseExistingServer: true,
    timeout: 300_000,
    env: {
      // rewrites 의 백엔드 주소는 빌드 시점에 정해진다.
      BACKEND_URL: process.env.BACKEND_URL ?? "http://localhost:8080",
    },
  },
});
