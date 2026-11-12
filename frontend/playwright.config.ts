import { defineConfig, devices } from "@playwright/test";

/**
 * C1 핵심 흐름 E2E. 실제 백엔드(`local,demo` 프로필)가 떠 있어야 한다.
 * - E2E_BASE_URL: 프론트 주소(기본 http://localhost:3100 — Grafana 의 3000 과 겹치지 않게). 그 주소에 서버가 없으면
 *   scripts/e2e-server.sh 로 .next-e2e 에 빌드해 next start 로 띄운다. 이미 떠 있으면 그 서버를 그대로 쓰므로(reuseExistingServer),
 *   globalSetup 이 KAIROS 화면인지 먼저 확인한다.
 * - BACKEND_URL: 백엔드 주소(기본 http://localhost:8080). 픽스처가 API 를 직접 부르고, 직접 띄우는 dev 서버도 이 주소로 프록시한다.
 * - KAIROS_DEMO_PASSWORD: 데모 관리자(admin@kairos.demo) 비밀번호. 없으면 관리자가 필요한 spec 은 건너뛴다.
 *   c4-settlement 는 데모 판매자(seller@kairos.demo)로도 로그인하므로 백엔드와 같은 값을 쓴다.
 *
 * C4 정산 E2E(c4-settlement.spec.ts)는 "지난주(한국 시간 기준 가장 최근에 끝난 월~일)" 정산을 만든다. 서버는 정산 시작일
 * (`kairos.settlement.start-date`, application.yaml 에 고정) 이후 기간만 오래된 순으로 생성하므로, 백엔드를 띄울 때
 * 시작일을 지난주 월요일로 덮어써야 한다(Spring 느슨한 바인딩 환경 변수). 시연 주문(§5-3)도 demo 시더가 같은 주에 만든다.
 *   export KAIROS_SETTLEMENT_STARTDATE=$(TZ=Asia/Seoul date -v-"$(( $(TZ=Asia/Seoul date +%u) - 1 + 7 ))"d +%F)   # macOS(BSD date)
 *   다른 시스템에서는 지난주 월요일(KST) 날짜를 YYYY-MM-DD 로 직접 넣는다.
 * 정산 spec 은 새 스키마에서만 한 번 돈다(같은 스키마에서 다시 돌리면 그 기간은 "이미 생성된 기간"으로 나온다). 한국 시간 월요일 00:00 을 넘겨 돌리지 않는다.
 * 새 빈 스키마에서 전체 스위트를 한 번에 돌린다(spec 은 이름순이고, c4 spec 은 앞선 spec 의 데이터에 기대지 않는다).
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
