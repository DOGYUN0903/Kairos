import { expect, test } from "@playwright/test";
import {
  adminToken,
  api,
  createSellerWithProduct,
  createUser,
  fillShippingAddress,
  firstOrderItemId,
  getPointBalance,
  payWithMock,
  placePaidOrderApi,
  requireAdmin,
  shipDeliverConfirm,
  uiLogin,
  uniq,
  valueOf,
  writeReview,
} from "./fixtures/api";

/** 견적 디바운스(500ms)보다 조금 길게 한 번만 기다려, 잘못된 입력에 요청이 나가지 않음을 확인한다. */
const NO_QUOTE_WAIT_MS = 700;

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

test("포인트로 결제하고 취소하면 사용·환급이 내역에 남는다", async ({ page, request }) => {
  test.setTimeout(240_000);
  const run = uniq();
  const admin = await adminToken(request);
  const a = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자A ${run}`, productName: `E2E 상품A ${run}`, price: 60000, stock: 10 });
  const e = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자E ${run}`, productName: `E2E 상품E ${run}`, price: 30000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });

  // 준비(API): 60,000원 주문 → 확정(600P) → 사진 후기(500P) → 잔액 1,100P.
  const first = await placePaidOrderApi(request, buyer.accessToken, [{ skuId: a.skuId, quantity: 1 }]);
  await shipDeliverConfirm(request, a.sellerToken, buyer.accessToken, first);
  expect(await getPointBalance(request, buyer.accessToken)).toBe(600);
  const review = await writeReview(request, buyer.accessToken, first.orderId, await firstOrderItemId(request, buyer.accessToken, first.orderId), { withPhoto: true });
  expect(review.rewardPoints).toBe(500);
  expect(await getPointBalance(request, buyer.accessToken)).toBe(1100);

  let quoteRequests = 0;
  let quoteRequestsBeforeMax = 0;
  await page.route("**/api/v1/orders/quote", async (route) => {
    quoteRequests += 1;
    await route.continue();
  });

  await uiLogin(page, buyer.email, buyer.password);
  await page.waitForURL((u) => u.pathname !== "/login");
  await page.goto(`/checkout?skuId=${e.skuId}&qty=1`);
  await expect(page.getByRole("heading", { name: "주문서" })).toBeVisible();
  await fillShippingAddress(page);

  const section = page.getByRole("region", { name: "할인·포인트" });
  const input = page.getByLabel("사용할 포인트");
  await test.step("최대 사용: 1,100P 가 들어가고 결제 금액이 서버 견적과 맞는다", async () => {
    await expect(section.getByText("보유 1,100P", { exact: true })).toBeVisible();
    quoteRequestsBeforeMax = quoteRequests;
    await section.getByRole("button", { name: "최대 사용" }).click();
    await expect(input).toHaveValue("1100");
    await expect(valueOf(page, "포인트 사용")).toHaveText("−1,100P");
    await expect(page.locator("strong.text-2xl")).toHaveText("31,900원");
    await expect(page.getByRole("button", { name: "31,900원 결제하기" })).toBeEnabled();
  });

  await test.step("10P 단위가 아니면 안내만 뜨고 견적 요청 없이 결제가 막힌다", async () => {
    // 양성 대조: 위 "최대 사용"은 견적을 다시 불렀다. 이 계측이 요청을 실제로 센다는 증거다.
    expect(quoteRequests).toBeGreaterThan(quoteRequestsBeforeMax);
    const before = quoteRequests;
    await input.fill("15");
    await expect(page.getByRole("alert").filter({ hasText: "10P 단위로 입력해 주세요" })).toBeVisible();
    await page.waitForTimeout(NO_QUOTE_WAIT_MS);
    expect(quoteRequests).toBe(before);
    await expect(page.getByRole("button", { name: /결제하기$/ })).toBeDisabled();

    await input.fill("1100");
    await expect(page.getByRole("button", { name: "31,900원 결제하기" })).toBeEnabled();
  });

  await page.getByRole("button", { name: "31,900원 결제하기" }).click();
  await page.waitForURL((u) => /\/my\/orders\/\d+\/pay$/.test(u.pathname));
  await payWithMock(page);

  await test.step("포인트 내역에 주문 사용 −1,100P", async () => {
    await page.goto("/my/points");
    await expect(page.getByText("보유 포인트", { exact: true }).locator("xpath=following-sibling::strong[1]")).toHaveText("0P");
    const row = page.getByRole("row").filter({ hasText: "주문 사용" });
    await expect(row.getByRole("cell").nth(2)).toHaveText("−1,100P");
  });

  await test.step("항목 취소: 주문 취소 환급 +1,100P, 잔액 1,100P", async () => {
    const orders = await api.get<{ content: { orderId: number }[] }>(request, "/api/v1/orders", buyer.accessToken);
    const secondId = orders.content.map((o) => o.orderId).find((id) => id !== first.orderId)!;
    await page.goto(`/my/orders/${secondId}`);
    const card = page.getByRole("region", { name: /E2E 판매자E .* 주문/ });
    await card.getByRole("button", { name: "취소", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "주문 항목 취소" });
    await dialog.getByLabel(/취소 사유/).fill("단순 변심");
    await dialog.getByRole("button", { name: "취소하기" }).click();
    await expect(dialog).toBeHidden();

    await page.goto("/my/points");
    const row = page.getByRole("row").filter({ hasText: "주문 취소 환급" });
    await expect(row.getByRole("cell").nth(2)).toHaveText("+1,100P");
    await expect(page.getByText("보유 포인트", { exact: true }).locator("xpath=following-sibling::strong[1]")).toHaveText("1,100P");
  });
});
