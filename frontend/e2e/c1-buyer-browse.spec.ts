import { expect, test } from "@playwright/test";
import { adminToken, api, createSellerWithProduct, DEMO_PASSWORD, leafCategory, uniq } from "./fixtures/api";

type Page_ = { totalElements: number };

/** 카드(목록 항목)의 첫 가격(판매가)을 원 단위 정수로. */
async function cardPrices(page: import("@playwright/test").Page): Promise<number[]> {
  const cards = page.getByRole("main").getByRole("listitem").filter({ hasText: "원" });
  const texts = await cards.allInnerTexts();
  return texts.map((t) => Number((/([\d,]+)원/.exec(t)?.[1] ?? "").replace(/,/g, ""))).filter((n) => Number.isFinite(n) && n > 0);
}

test("구매자 목록: 카테고리·정렬·검색·페이지가 URL 에 실리고 뒤로 가기로 복원된다", async ({ page, request }) => {
  const MIN_PRODUCTS = 21; // 한 페이지 20개 + 2페이지 확인
  let all = await api.get<Page_>(request, "/api/v1/products?size=20", null);
  // 데모 백엔드(비밀번호가 주어진 경우)라면 모자란 판매 중 상품을 API 로 채우고 다시 센다. 없으면 건너뛴다.
  if (DEMO_PASSWORD && all.totalElements < MIN_PRODUCTS) {
    const admin = await adminToken(request);
    for (let i = all.totalElements; i < MIN_PRODUCTS; i++) {
      const id = uniq();
      await createSellerWithProduct(request, admin, { sellerName: `E2E 목록 ${id}`, productName: `E2E 목록 상품 ${id}`, price: 10000, stock: 10 });
    }
    all = await api.get<Page_>(request, "/api/v1/products?size=20", null);
  }
  const reason = `상품이 ${all.totalElements}개뿐이라 2페이지가 없습니다(데모 데이터 필요).`;
  if (DEMO_PASSWORD) expect(all.totalElements, reason).toBeGreaterThanOrEqual(MIN_PRODUCTS);
  else test.skip(all.totalElements < MIN_PRODUCTS, reason);

  await page.goto("/products");
  const { parentId, parentName } = await leafCategory(request);
  await page.getByRole("navigation", { name: "카테고리" }).getByRole("link", { name: parentName, exact: true }).click();
  await page.waitForURL(new RegExp(`categoryId=${parentId}(&|$)`));

  await page.goto("/products");
  await page.getByRole("group", { name: "정렬" }).getByRole("button", { name: "낮은 가격순" }).click();
  await page.waitForURL(/sort=PRICE_ASC/);
  // 이전 결과를 유지한 채 새 정렬을 받아 오므로, 정렬된 목록이 그려질 때까지 기다린다.
  await expect
    .poll(async () => {
      const p = await cardPrices(page);
      return p.length > 1 && p.every((v, i) => i === 0 || p[i - 1]! <= v);
    })
    .toBe(true);
  const prices = await cardPrices(page);
  expect(prices[0]!).toBeLessThanOrEqual(prices[1]!);

  const sortedUrl = page.url();
  await page.getByRole("navigation", { name: "페이지" }).getByRole("button", { name: "2", exact: true }).click();
  await page.waitForURL(/page=1/);
  await expect.poll(async () => (await cardPrices(page))[0] ?? 0).toBeGreaterThanOrEqual(prices[prices.length - 1]!);

  await page.getByLabel("상품명 또는 브랜드 검색").fill("E2E");
  await page.getByLabel("상품명 또는 브랜드 검색").press("Enter");
  await page.waitForURL(/keyword=E2E/);

  await page.goBack();
  await page.waitForURL(/page=1/);
  await page.goBack();
  await expect(page).toHaveURL(sortedUrl);
  await expect(page.getByRole("group", { name: "정렬" }).getByRole("button", { name: "낮은 가격순" })).toHaveAttribute("aria-pressed", "true");
});
