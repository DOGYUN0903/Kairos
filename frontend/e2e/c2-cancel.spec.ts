import { expect, test, type Page } from "@playwright/test";
import { adminToken, asUser, createSellerWithProduct, createUser, placePaidOrderApi, requireAdmin, uiLogin, uniq } from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

async function setup(request: Parameters<typeof createUser>[0]) {
  const run = uniq();
  const admin = await adminToken(request);
  const b = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자B ${run}`, productName: `E2E 상품B ${run}`, price: 30000, stock: 10 });
  const c = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자C ${run}`, productName: `E2E 상품C ${run}`, price: 30000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });
  const order = await placePaidOrderApi(request, buyer.accessToken, [
    { skuId: b.skuId, quantity: 1 },
    { skuId: c.skuId, quantity: 1 },
  ]);
  return { run, b, c, buyer, order };
}

async function loginBuyer(page: Page, email: string, password: string) {
  await uiLogin(page, email, password);
  await page.waitForURL((u) => u.pathname !== "/login");
}

test("구매자가 항목을 하나씩 취소하면 배송비 차감 안내와 환불 금액이 맞고 모두 취소하면 주문이 취소된다", async ({ page, request }) => {
  const { run, buyer, order } = await setup(request);
  await loginBuyer(page, buyer.email, buyer.password);
  await page.goto(`/my/orders/${order.orderId}`);

  const summary = page.getByRole("region", { name: "결제 요약" });
  await expect(summary.getByText("60,000원").first()).toBeVisible();

  await test.step("C 항목 취소: 배송비 3,000원 차감, 환불 27,000원", async () => {
    const card = page.getByRole("region", { name: /E2E 판매자C .* 주문/ });
    await card.getByRole("button", { name: "취소", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "주문 항목 취소" });
    await expect(dialog.getByText("배송비 차감", { exact: true }).locator("..")).toContainText("3,000원");
    await expect(dialog.getByText("환불 예정 금액", { exact: true }).locator("..")).toContainText("27,000원");
    await dialog.getByLabel(/취소 사유/).fill("단순 변심");
    await dialog.getByRole("button", { name: "취소하기" }).click();
    await expect(dialog).toBeHidden();
    await expect(card.getByText("취소", { exact: true })).toBeVisible();
    await expect(summary.getByText("환불 금액", { exact: true }).locator("..")).toContainText("27,000원");
  });

  await test.step("B 항목 취소: 차감분이 돌아와 환불 33,000원, 주문 취소, 환불 합계 60,000원", async () => {
    const card = page.getByRole("region", { name: /E2E 판매자B .* 주문/ });
    await card.getByRole("button", { name: "취소", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "주문 항목 취소" });
    await expect(dialog.getByText("환불 예정 금액", { exact: true }).locator("..")).toContainText("33,000원");
    await dialog.getByLabel(/취소 사유/).fill("단순 변심");
    await dialog.getByRole("button", { name: "취소하기" }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByRole("heading", { name: "취소", exact: true })).toBeVisible();
    await expect(summary.getByText("환불 금액", { exact: true }).locator("..")).toContainText("60,000원");
  });
  void run;
});

test("판매자가 배송 전 항목을 취소하면 배송비 차감 없이 전액 환불된다", async ({ page, request }) => {
  const { b, buyer, order } = await setup(request);

  await asUser(page, b.sellerEmail, "e2e-password-1", async (s) => {
    await s.goto("/seller/orders");
    await s.getByRole("tab", { name: /발송 대기/ }).click();
    await s.getByRole("table").getByRole("link").first().click();
    await s.getByRole("button", { name: "항목 취소" }).click();
    const dialog = s.getByRole("dialog", { name: "주문 항목 취소" });
    await expect(dialog.getByText("배송비 차감 없이 환불됩니다")).toBeVisible();
    await dialog.getByLabel(/취소 사유/).fill("재고 부족");
    await dialog.getByRole("button", { name: "취소하기" }).click();
    await expect(dialog).toBeHidden();
    await expect(s.getByText("판매자 취소")).toBeVisible();
  });

  await loginBuyer(page, buyer.email, buyer.password);
  await page.goto(`/my/orders/${order.orderId}`);
  const summary = page.getByRole("region", { name: "결제 요약" });
  await expect(summary.getByText("환불 금액", { exact: true }).locator("..")).toContainText("30,000원");
  await expect(summary.getByText("무료배송 기준 미달로 차감된 배송비")).toHaveCount(0);
  const card = page.getByRole("region", { name: /E2E 판매자B .* 주문/ });
  await expect(card.getByText("환불 30,000원")).toBeVisible();
});
