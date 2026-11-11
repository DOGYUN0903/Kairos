import type { FullConfig } from "@playwright/test";

/** reuseExistingServer 로 엉뚱한 서버(Grafana 등)를 쓰지 않게, baseURL 이 KAIROS 프론트인지 먼저 확인한다. */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use.baseURL;
  if (!baseURL) throw new Error("baseURL 이 없습니다");
  const res = await fetch(baseURL);
  const html = await res.text();
  if (!res.ok || !/<title>KAIROS<\/title>/.test(html)) {
    throw new Error(`${baseURL} 는 KAIROS 프론트가 아닙니다(status ${res.status}). E2E_BASE_URL 을 확인하세요.`);
  }
}
