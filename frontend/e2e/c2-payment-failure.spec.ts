import { expect, test, type Page } from "@playwright/test";
import { adminToken, api, createSellerWithProduct, createUser, fillShippingAddress, payWithMock, requireAdmin, uiLogin, uniq } from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

async function setup(request: Parameters<typeof createUser>[0], page: Page) {
  const run = uniq();
  const admin = await adminToken(request);
  const s = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자 ${run}`, productName: `E2E 상품 ${run}`, price: 30000, stock: 5 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });
  await api.post(request, "/api/v1/cart/items", buyer.accessToken, { skuId: s.skuId, quantity: 1 });
  await uiLogin(page, buyer.email, buyer.password);
  await page.waitForURL((u) => u.pathname !== "/login");
  return { s, buyer, productName: `E2E 상품 ${run}` };
}

/** 장바구니 → 주문서 → 결제하기까지 눌러 결제 페이지(모의 결제창)에 도달한다. */
async function orderFromCart(page: Page) {
  await page.goto("/cart");
  await page.getByRole("complementary", { name: "결제 예정 금액" }).getByRole("link", { name: /주문하기/ }).click();
  await fillShippingAddress(page);
  const pay = page.getByRole("button", { name: /결제하기$/ });
  await expect(pay).toBeEnabled();
  await pay.click();
  await page.waitForURL((u) => /\/my\/orders\/\d+\/pay$/.test(u.pathname));
  await expect(page.getByRole("dialog", { name: /결제/ })).toBeVisible();
}

async function stockOf(request: Parameters<typeof createUser>[0], sellerToken: string, productId: number): Promise<number> {
  const d = await api.get<{ skus: { stock: number }[] }>(request, `/api/v1/seller/products/${productId}`, sellerToken);
  return d.skus[0]!.stock;
}

/** 결제 승인 요청의 paymentKey 앞에 접두사를 붙인다(모의 PG 가 접두사로 결과를 고른다). */
async function prefixPaymentKey(page: Page, prefix: string) {
  await page.route("**/api/v1/payments/confirm", async (route) => {
    const body = route.request().postDataJSON() as { paymentKey: string };
    await route.continue({ postData: JSON.stringify({ ...body, paymentKey: `${prefix}${body.paymentKey}` }) });
  });
}

test("결제창을 닫으면 결제 대기로 남고 다시 결제할 수 있다", async ({ page, request }) => {
  await setup(request, page);
  await orderFromCart(page);

  await page.getByRole("dialog", { name: /결제/ }).getByRole("button", { name: "닫기" }).click();
  await page.getByRole("link", { name: "주문 상세" }).click();
  await page.waitForURL((u) => /\/my\/orders\/\d+$/.test(u.pathname));
  await expect(page.getByRole("heading", { name: "결제 대기" })).toBeVisible();
  await expect(page.getByRole("timer")).toBeVisible();

  await page.getByRole("link", { name: "결제하기" }).click();
  await page.waitForURL((u) => /\/my\/orders\/\d+\/pay$/.test(u.pathname));
  await page.getByRole("button", { name: "결제하기" }).click();
  await payWithMock(page);
});

test("결제가 거절되면 주문이 취소되고 장바구니는 그대로다", async ({ page, request }) => {
  const { s, productName } = await setup(request, page);
  const before = await stockOf(request, s.sellerToken, s.productId);

  await prefixPaymentKey(page, "fail-");
  await orderFromCart(page);
  await page.getByRole("dialog", { name: /결제/ }).getByRole("button", { name: "결제", exact: true }).click();
  await expect(page.getByText("결제가 거절되어 주문이 취소되었습니다")).toBeVisible();

  await page.getByRole("link", { name: "장바구니로" }).click();
  await expect(page.getByRole("link", { name: productName })).toBeVisible();
  expect(await stockOf(request, s.sellerToken, s.productId)).toBe(before);

  await page.unroute("**/api/v1/payments/confirm");
  await orderFromCart(page);
  await payWithMock(page);
});

test("결제 응답을 받지 못하면 확인 중으로 보인다", async ({ page, request }) => {
  await setup(request, page);
  await prefixPaymentKey(page, "unknown-");
  await orderFromCart(page);
  await page.getByRole("dialog", { name: /결제/ }).getByRole("button", { name: "결제", exact: true }).click();

  await expect(page.getByText("결제 결과를 확인하고 있습니다")).toBeVisible();
  await expect(page.getByText(/주문 완료|거절/)).toHaveCount(0);
});
