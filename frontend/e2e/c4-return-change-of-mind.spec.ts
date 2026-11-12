import { expect, test } from "@playwright/test";
import {
  addProductToSeller,
  adminToken,
  api,
  approveReturnApi,
  asUser,
  createSellerWithProduct,
  createUser,
  itemRow,
  placePaidOrderApi,
  receiveReturnApi,
  refundValue,
  requireAdmin,
  shipAndDeliver,
  uiLogin,
  uniq,
  valueOf,
} from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

const ESTIMATE_NOTE = "실제 환불 금액은 판매자가 상품을 받은 뒤 확정됩니다. 같은 주문의 다른 상품을 취소·반품하면 달라질 수 있습니다";

test("단순 변심 반품: 환불 예정 금액 미리보기부터 판매자 승인·수거 확인, 남은 항목 반품, 전체 반품 주문까지 이어진다", async ({ page, request }) => {
  test.setTimeout(300_000);
  const run = uniq();
  const admin = await adminToken(request);
  const sellerName = `E2E 판매자S ${run}`;
  const nameA = `E2E 상품S4 ${run}`;
  const nameB = `E2E 상품S2 ${run}`;
  // 판매자 S 의 40,000원 + 20,000원을 한 주문으로(합계 60,000원 → 무료배송).
  const s = await createSellerWithProduct(request, admin, { sellerName, productName: nameA, price: 40000, stock: 10 });
  const second = await addProductToSeller(request, admin, s, { sellerName, productName: nameB, price: 20000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });
  const order = await placePaidOrderApi(request, buyer.accessToken, [
    { skuId: s.skuId, quantity: 1 },
    { skuId: second.skuId, quantity: 1 },
  ]);
  await shipAndDeliver(request, s.sellerToken, buyer.accessToken, order);

  await uiLogin(page, buyer.email, buyer.password);
  await page.waitForURL((u) => u.pathname !== "/login");
  await page.goto(`/my/orders/${order.orderId}`);

  await test.step("20,000원 항목: 반품 가능 기한과 요청 창의 환불 예정 금액", async () => {
    const row = itemRow(page, nameB);
    await expect(row.getByText(/까지 반품 가능$/)).toBeVisible();
    await row.getByRole("button", { name: "반품 요청" }).click();
    const dialog = page.getByRole("dialog", { name: "반품 요청" });
    await expect(dialog.getByText("사유를 선택하면 환불 예정 금액을 보여 드립니다")).toBeVisible();
    await dialog.getByRole("radio", { name: "단순 변심" }).check();

    await expect(valueOf(dialog, "결제 금액")).toHaveText("20,000원");
    await expect(valueOf(dialog, "반품비")).toHaveText("−3,000원");
    await expect(valueOf(dialog, "발송 배송비 회수")).toHaveText("−3,000원");
    await expect(refundValue(dialog, "환불 예정 금액")).toHaveText("14,000원");
    await expect(dialog.locator("dt em").filter({ hasText: "예상" })).toBeVisible();
    await expect(dialog.getByText(ESTIMATE_NOTE, { exact: true })).toBeVisible();

    // 연타해도 한 건만 만들어진다.
    await dialog.getByRole("button", { name: "반품 요청", exact: true }).dblclick();
    await expect(dialog).toBeHidden();
  });

  await test.step("반품 내역에 1건", async () => {
    await page.goto("/my/returns");
    await expect(page.locator('a[href^="/my/returns/"]')).toHaveCount(1);
    await expect(page.getByText(nameB, { exact: true })).toBeVisible();
  });

  await asUser(page, s.sellerEmail, "e2e-password-1", async (sp) => {
    await test.step("판매자: 요청 탭 → 상세 → 승인", async () => {
      await sp.goto("/seller/returns");
      await sp.getByRole("tab", { name: /^요청/ }).click();
      await expect(sp).toHaveURL(/status=REQUESTED/);
      await sp.getByRole("link", { name: /반품 보기$/ }).click();
      await sp.waitForURL(/\/seller\/returns\/\d+$/);
      await sp.getByRole("button", { name: "승인", exact: true }).click();
      const dialog = sp.getByRole("dialog", { name: "반품 승인" });
      await dialog.getByRole("button", { name: "승인", exact: true }).click();
      await expect(dialog).toBeHidden();
    });
    await test.step("판매자: 수거 확인 창의 재고 복구 안내 → 확인 → 환불 금액 14,000원", async () => {
      await sp.getByRole("button", { name: "수거 확인", exact: true }).click();
      const dialog = sp.getByRole("dialog", { name: /수거 확인/ });
      await expect(dialog.getByText("단순 변심 반품이라 환불이 완료되면 재고가 자동으로 복구됩니다.")).toBeVisible();
      await dialog.getByRole("button", { name: "수거 확인 · 환불 실행" }).click();
      await expect(sp.getByText("환불을 완료했습니다. 환불 금액 14,000원").first()).toBeVisible();
    });
  });

  await test.step("구매자: 반품 상세의 환불 금액 14,000원", async () => {
    await page.goto("/my/returns");
    await page.locator('a[href^="/my/returns/"]').click();
    await page.waitForURL(/\/my\/returns\/\d+$/);
    await expect(valueOf(page, "환불 금액")).toHaveText("14,000원");
  });

  await test.step("40,000원 항목: 발송 배송비 회수 없이 환불 예정 37,000원, 요청", async () => {
    await page.goto(`/my/orders/${order.orderId}`);
    await itemRow(page, nameA).getByRole("button", { name: "반품 요청" }).click();
    const dialog = page.getByRole("dialog", { name: "반품 요청" });
    await dialog.getByRole("radio", { name: "단순 변심" }).check();
    await expect(valueOf(dialog, "반품비")).toHaveText("−3,000원");
    await expect(refundValue(dialog, "환불 예정 금액")).toHaveText("37,000원");
    await expect(dialog.locator("dt").filter({ hasText: "발송 배송비 회수" })).toHaveCount(0);
    await dialog.getByRole("button", { name: "반품 요청", exact: true }).click();
    await expect(dialog).toBeHidden();
  });

  // 승인·수거 확인은 API 로 처리한다.
  const mine = await api.get<{ content: { returnId: number; status: string }[] }>(request, "/api/v1/returns/me", buyer.accessToken);
  const pending = mine.content.find((r) => r.status === "REQUESTED");
  if (!pending) throw new Error("승인할 반품이 없습니다");
  await approveReturnApi(request, s.sellerToken, pending.returnId);
  const received = await receiveReturnApi(request, s.sellerToken, pending.returnId);
  expect(received.refundAmount).toBe(37000);

  await test.step("주문 상세: 전체 반품 칩, 환불 금액 51,000원, 반품 배송비 차감 안내", async () => {
    await page.goto(`/my/orders/${order.orderId}`);
    const card = page.getByRole("region", { name: `${sellerName} 상사 주문` });
    await expect(card.getByText("전체 반품", { exact: true })).toBeVisible();
    const summary = page.getByRole("region", { name: "결제 요약" });
    // 결제 요약의 환불 금액 줄은 부호를 앞에 붙여 보인다(c2-cancel 과 같은 표기).
    await expect(valueOf(summary, "환불 금액")).toHaveText("−51,000원");
    await expect(summary.getByText("반품 배송비가 차감된 금액입니다")).toBeVisible();
  });
});
