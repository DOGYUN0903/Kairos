import { expect, test, type Page } from "@playwright/test";
import {
  adminToken,
  api,
  asUser,
  changeSalePrice,
  createSellerWithProduct,
  createUser,
  fillShippingAddress,
  payWithMock,
  requireAdmin,
  uiLogin,
  uniq,
  valueOf,
} from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

async function addToCart(page: Page, productId: number, productName: string) {
  await page.goto(`/products/${productId}`);
  await expect(page.getByRole("heading", { name: productName })).toBeVisible();
  await page.getByRole("button", { name: "장바구니", exact: true }).click();
  await expect(page.getByRole("status").filter({ hasText: "장바구니에 담았습니다" })).toBeVisible();
}

test("장바구니 두 판매자 묶음을 한 번에 주문하고 결제·발송·배송 완료·구매 확정까지 이어진다", async ({ page, request }) => {
  test.setTimeout(240_000);
  const run = uniq();
  const admin = await adminToken(request);
  const b = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자B ${run}`, productName: `E2E 상품B ${run}`, price: 20000, stock: 10 });
  const c = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자C ${run}`, productName: `E2E 상품C ${run}`, price: 20000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });

  await uiLogin(page, buyer.email, buyer.password);
  await page.waitForURL((u) => u.pathname !== "/login");

  await test.step("상품 상세에서 장바구니에 담는다", async () => {
    await addToCart(page, b.productId, `E2E 상품B ${run}`);
    await addToCart(page, c.productId, `E2E 상품C ${run}`);
    await expect(page.getByRole("link", { name: "장바구니 2개" })).toBeVisible();
  });

  await test.step("장바구니: 판매자 묶음 2개, 배송비, 결제 예정 금액", async () => {
    await page.goto("/cart");
    await expect(page.getByRole("region", { name: /상품$/ })).toHaveCount(2);
    const summary = page.getByRole("complementary", { name: "결제 예정 금액" });
    // 금액은 글자 전체로 단언한다("3,000원" 부분 일치는 13,000원·33,000원도 통과한다).
    await expect(valueOf(summary, "상품 금액")).toHaveText("40,000원");
    await expect(valueOf(summary, "배송비")).toHaveText("6,000원");
    await expect(summary.getByText("3,000원", { exact: true })).toHaveCount(2);
    await expect(valueOf(summary, "결제 예정 금액")).toHaveText("46,000원");
    await expect(summary.getByText("더 담으면 무료배송")).toHaveText("10,000원 더 담으면 무료배송");
    await summary.getByRole("link", { name: /주문하기/ }).click();
  });

  await test.step("주문서: 배송지 입력 후 결제하기를 연타해도 주문은 한 건", async () => {
    await expect(page.getByRole("heading", { name: "주문서" })).toBeVisible();
    await fillShippingAddress(page);
    const pay = page.getByRole("button", { name: "46,000원 결제하기" });
    await expect(pay).toBeEnabled();
    await pay.dblclick();
    await page.waitForURL((u) => /\/my\/orders\/\d+\/pay$/.test(u.pathname));
    await payWithMock(page);
  });

  const orders = await api.get<{ content: { orderId: number; orderNo: string }[] }>(request, "/api/v1/orders", buyer.accessToken);
  expect(orders.content).toHaveLength(1);
  const orderId = orders.content[0]!.orderId;

  await test.step("헤더 장바구니 배지가 사라지고 주문 내역에 배송 준비 중으로 보인다", async () => {
    await expect(page.getByRole("link", { name: "장바구니", exact: true })).toBeVisible();
    await page.goto("/my/orders");
    await expect(page.getByText("배송 준비 중")).toBeVisible();
    await page.getByRole("link", { name: "주문 상세", exact: true }).click();
    await page.waitForURL(`**/my/orders/${orderId}`);
    await expect(page.getByRole("heading", { name: "결제 완료" })).toBeVisible();
  });

  await test.step("판매자 B: 발송 처리 → 배송 완료", async () => {
    await asUser(page, b.sellerEmail, "e2e-password-1", async (s) => {
      await s.goto("/seller/orders");
      await s.getByRole("tab", { name: /발송 대기/ }).click();
      await s.getByRole("table").getByRole("link").first().click();
      await s.getByRole("button", { name: "발송 처리" }).click();
      const dialog = s.getByRole("dialog", { name: "발송 처리" });
      await dialog.getByLabel("택배사").selectOption({ label: "CJ대한통운" });
      await dialog.getByLabel("송장번호").fill("1234567890");
      await dialog.getByRole("button", { name: "발송 처리" }).click();
      await expect(s.getByText("1234567890")).toBeVisible();
      await s.getByRole("button", { name: "배송 완료" }).click();
      await s.getByRole("dialog").getByRole("button", { name: "배송 완료" }).click();
      await expect(s.getByText("배송 완료로 처리했습니다")).toBeVisible();
    });
  });

  await test.step("구매자: B 카드 배송 완료·택배사 → 구매 확정", async () => {
    await page.goto(`/my/orders/${orderId}`);
    const card = page.getByRole("region", { name: /E2E 판매자B .* 주문/ });
    await expect(card.getByText("배송 완료")).toBeVisible();
    await expect(card.getByText("CJ대한통운")).toBeVisible();
    await card.getByRole("button", { name: "구매 확정" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "구매 확정" }).click();
    await expect(card.getByText("구매 확정", { exact: true })).toBeVisible();
    await expect(card.getByRole("button", { name: "구매 확정" })).toHaveCount(0);
  });
});

test("가격이 바뀌면 안내하고 다시 결제할 수 있다", async ({ page, request }) => {
  const run = uniq();
  const admin = await adminToken(request);
  const s = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자 ${run}`, productName: `E2E 상품 ${run}`, price: 20000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });
  await uiLogin(page, buyer.email, buyer.password);
  await page.waitForURL((u) => u.pathname !== "/login");
  await addToCart(page, s.productId, `E2E 상품 ${run}`);
  await page.goto("/cart");
  await page.getByRole("complementary", { name: "결제 예정 금액" }).getByRole("link", { name: /주문하기/ }).click();
  await fillShippingAddress(page);
  await expect(page.getByRole("button", { name: "23,000원 결제하기" })).toBeEnabled();

  await changeSalePrice(request, s.sellerToken, s.productId, 19000);

  await page.getByRole("button", { name: "23,000원 결제하기" }).click();
  await expect(page.getByText("금액이 변경되었습니다")).toBeVisible();
  const again = page.getByRole("button", { name: "22,000원 결제하기" });
  await expect(again).toBeEnabled();
  await again.click();
  await page.waitForURL((u) => /\/my\/orders\/\d+\/pay$/.test(u.pathname));
  await expect(page.getByRole("dialog", { name: /결제/ })).toBeVisible();
});
