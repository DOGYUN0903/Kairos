import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, DEMO_PASSWORD, adminToken, asUser, createSellerWithProduct, createUser, itemRow, placePaidOrderApi, refundValue, requireAdmin, sellerId, shipAndDeliver, uiLogin, uniq, valueOf } from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

test("상품 불량 반품: 환불 예정 33,000원, 구매 확정 차단, 철회·재요청, 판매자 거절, 관리자 재승인과 대신 수거 확인", async ({ page, request }) => {
  test.setTimeout(120_000);
  const run = uniq();
  const admin = await adminToken(request);
  const productName = `E2E 상품T ${run}`;
  // 30,000원 + 배송비 3,000원(구매자 부담) = 결제 33,000원.
  const t = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자T ${run}`, productName, price: 30000, stock: 10 });
  const tSellerId = await sellerId(request, t.sellerToken);
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });
  const order = await placePaidOrderApi(request, buyer.accessToken, [{ skuId: t.skuId, quantity: 1 }]);
  await shipAndDeliver(request, t.sellerToken, buyer.accessToken, order);

  await uiLogin(page, buyer.email, buyer.password);
  await page.waitForURL((u) => u.pathname !== "/login");
  const orderUrl = `/my/orders/${order.orderId}`;
  await page.goto(orderUrl);
  const confirmButton = page.getByRole("button", { name: "구매 확정", exact: true });

  const openRequestDialog = async () => {
    await itemRow(page, productName).getByRole("button", { name: "반품 요청" }).click();
    const dialog = page.getByRole("dialog", { name: "반품 요청" });
    await dialog.getByRole("radio", { name: "상품 불량" }).check();
    return dialog;
  };

  await test.step("요청 창: 판매자 부담이라 배송비 환불 +3,000원, 환불 예정 33,000원", async () => {
    const dialog = await openRequestDialog();
    await expect(valueOf(dialog, "배송비 환불")).toHaveText("+3,000원");
    await expect(refundValue(dialog, "환불 예정 금액")).toHaveText("33,000원");
    await expect(dialog.getByText("판매자 부담이라 차감이 없습니다", { exact: true })).toBeVisible();
    await dialog.getByRole("button", { name: "반품 요청", exact: true }).click();
    await expect(dialog).toBeHidden();
  });

  await test.step("반품이 열려 있는 동안 구매 확정 버튼은 비활성이고 이유가 보인다", async () => {
    await expect(confirmButton).toBeDisabled();
    await expect(page.getByText("반품이 진행 중인 상품이 있어 확정할 수 없습니다", { exact: true })).toBeVisible();
  });

  await test.step("철회하면 구매 확정 버튼이 다시 활성이다", async () => {
    await itemRow(page, productName).getByRole("button", { name: "철회", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "반품 철회" });
    await dialog.getByRole("button", { name: "반품 철회", exact: true }).click();
    await expect(dialog).toBeHidden();
    await expect(confirmButton).toBeEnabled();
  });

  await test.step("다시 요청", async () => {
    const dialog = await openRequestDialog();
    await expect(refundValue(dialog, "환불 예정 금액")).toHaveText("33,000원");
    await dialog.getByRole("button", { name: "반품 요청", exact: true }).click();
    await expect(dialog).toBeHidden();
    await expect(confirmButton).toBeDisabled();
  });

  await asUser(page, t.sellerEmail, "e2e-password-1", async (sp) => {
    await test.step("판매자 거절(사유 입력)", async () => {
      await sp.goto("/seller/returns?status=REQUESTED");
      await sp.getByRole("link", { name: /반품 보기$/ }).click();
      await sp.waitForURL(/\/seller\/returns\/\d+$/);
      await sp.getByRole("button", { name: "거절", exact: true }).click();
      const dialog = sp.getByRole("dialog", { name: "반품 거절" });
      await dialog.getByLabel(/거절 사유/).fill("불량 확인 불가");
      await dialog.getByRole("button", { name: "거절", exact: true }).click();
      await expect(dialog).toBeHidden();
      await expect(sp.getByText("반품을 거절했습니다").first()).toBeVisible();
    });
  });

  await test.step("구매자: 반품 거절과 사유, 다시 요청할 수 없다", async () => {
    await page.goto(orderUrl);
    const row = itemRow(page, productName);
    await expect(row.getByText("반품 거절", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "반품 요청" })).toHaveCount(0);
    await expect(row.getByText("반품이 거절된 상품입니다", { exact: true })).toBeVisible();
    await row.getByRole("link", { name: "반품 상세" }).click();
    await page.waitForURL(/\/my\/returns\/\d+$/);
    const rejected = page.getByRole("alert").filter({ hasText: "거절 사유" });
    await expect(rejected).toContainText("불량 확인 불가");
  });

  await asUser(page, ADMIN_EMAIL, DEMO_PASSWORD, async (ap) => {
    await test.step("관리자: 목록에서 그 건 → 재승인 → 대신 수거 확인 → 환불 금액 33,000원", async () => {
      await ap.goto(`/admin/returns?sellerId=${tSellerId}`);
      await ap.getByRole("link", { name: /반품 보기$/ }).click();
      await ap.waitForURL(/\/admin\/returns\/\d+$/);
      await ap.getByRole("button", { name: "승인", exact: true }).click();
      const approve = ap.getByRole("dialog", { name: "반품 승인" });
      await approve.getByRole("button", { name: "승인", exact: true }).click();
      await expect(approve).toBeHidden();

      await ap.getByRole("button", { name: "대신 수거 확인", exact: true }).click();
      const receive = ap.getByRole("dialog", { name: /수거 확인/ });
      await receive.getByRole("button", { name: "수거 확인 · 환불 실행" }).click();
      await expect(ap.getByText("환불을 완료했습니다. 환불 금액 33,000원").first()).toBeVisible();
      await expect(refundValue(ap, "환불 금액")).toHaveText("33,000원");
    });
  });

  await test.step("구매자 주문 상세의 환불 금액 33,000원", async () => {
    await page.goto(orderUrl);
    await expect(valueOf(page.getByRole("region", { name: "결제 요약" }), "환불 금액")).toHaveText("−33,000원");
  });
});
