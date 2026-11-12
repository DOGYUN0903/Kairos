import { expect, test, type Page } from "@playwright/test";
import {
  addCartItem,
  adminToken,
  api,
  asUser,
  claimCoupon,
  createAdminCouponPolicy,
  createSellerWithProduct,
  createUser,
  endAdminCouponPolicy,
  fillShippingAddress,
  kstLocal,
  payWithMock,
  requireAdmin,
  uiLogin,
  uniq,
  valueOf,
} from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

/** 판매자 쿠폰 관리 화면의 등록 양식으로 "10% 최대 5,000원" 상품 쿠폰(내 상품 전체)을 만든다. */
async function registerSellerRateCouponByForm(page: Page, name: string) {
  await page.goto("/seller/coupons");
  await page.getByRole("button", { name: "쿠폰 등록" }).first().click();
  const dialog = page.getByRole("dialog", { name: "쿠폰 등록" });
  await dialog.getByLabel(/^쿠폰 이름/).fill(name);
  await dialog.getByRole("radio", { name: "정률 할인" }).check();
  await dialog.getByLabel(/^할인율/).fill("10");
  await dialog.getByLabel(/^최대 할인 금액/).fill("5000");
  await dialog.getByLabel(/^시작/).fill(kstLocal(-1).slice(0, 16));
  await dialog.getByLabel(/^종료/).fill(kstLocal(30).slice(0, 16));
  await dialog.getByRole("button", { name: "쿠폰 등록", exact: true }).click();
  await expect(dialog).toBeHidden();
  // %→bp 변환이 맞는지 표에서 확인한다.
  const row = page.getByRole("row").filter({ hasText: name });
  await expect(row.getByText("10% 할인 (최대 5,000원)", { exact: true })).toBeVisible();
}

test("쿠폰 받기부터 상품·장바구니 쿠폰 결제, 사용 완료 목록, 항목 취소 복구까지 이어진다", async ({ page, request }) => {
  test.setTimeout(300_000);
  const run = uniq();
  const admin = await adminToken(request);
  const b = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자B ${run}`, productName: `E2E 상품B ${run}`, price: 30000, stock: 10 });
  const c = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자C ${run}`, productName: `E2E 상품C ${run}`, price: 20000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });

  const n2000 = `E2E 플랫폼 2천원 ${run}`;
  const nSeller = `E2E 판매자C 10% ${run}`;
  const nCart = `E2E 장바구니 10% ${run}`;
  const nMin = `E2E 플랫폼 1천원 최소3.5만 ${run}`;
  const policyIds: number[] = [];
  try {
    policyIds.push(await createAdminCouponPolicy(request, admin, { name: n2000, couponType: "PRODUCT", targetType: "ALL_PRODUCTS", discountType: "FIXED", discountValue: 2000 }));
    policyIds.push(
      await createAdminCouponPolicy(request, admin, { name: nCart, couponType: "CART", targetType: "NONE", discountType: "RATE", discountValue: 1000, maxDiscount: 5000, minOrderAmount: 50000 }),
    );
    const minPolicy = await createAdminCouponPolicy(request, admin, { name: nMin, couponType: "PRODUCT", targetType: "ALL_PRODUCTS", discountType: "FIXED", discountValue: 1000, minOrderAmount: 35000 });
    policyIds.push(minPolicy);

    // 판매자 C 쿠폰만 화면의 등록 양식으로 만든다.
    await asUser(page, c.sellerEmail, "e2e-password-1", async (s) => registerSellerRateCouponByForm(s, nSeller));

    await uiLogin(page, buyer.email, buyer.password);
    await page.waitForURL((u) => u.pathname !== "/login");

    await test.step("상품 상세에서 받기: 연타해도 한 장, 새로고침하면 받음", async () => {
      await page.goto(`/products/${b.productId}`);
      const zone = page.getByRole("region", { name: "쿠폰" });
      const card = zone.getByRole("listitem").filter({ hasText: n2000 });
      await card.getByRole("button", { name: "쿠폰 받기" }).dblclick();
      await expect(card.getByRole("button", { name: "받음" })).toBeDisabled();
      await page.reload();
      await expect(page.getByRole("region", { name: "쿠폰" }).getByRole("listitem").filter({ hasText: n2000 }).getByRole("button", { name: "받음" })).toBeVisible();

      await page.goto("/my/coupons?tab=mine");
      await expect(page.getByText(n2000, { exact: true })).toHaveCount(1);
    });

    // 나머지는 API 로 받는다(판매자 C 쿠폰은 정책 목록에서 이름으로 찾는다).
    const sellerList = await api.get<{ content: { policyId: number; name: string }[] }>(request, "/api/v1/seller/coupons/policies", c.sellerToken);
    const sellerPolicy = sellerList.content.find((p) => p.name === nSeller);
    if (!sellerPolicy) throw new Error("판매자 C 쿠폰 정책이 없습니다");
    await claimCoupon(request, buyer.accessToken, sellerPolicy.policyId);
    await claimCoupon(request, buyer.accessToken, policyIds[1]!);
    await claimCoupon(request, buyer.accessToken, minPolicy);

    const bItem = await addCartItem(request, buyer.accessToken, b.skuId, 1);
    const cItem = await addCartItem(request, buyer.accessToken, c.skuId, 2);
    await page.goto(`/checkout?cartItemIds=${bItem},${cItem}`);
    await expect(page.getByRole("heading", { name: "주문서" })).toBeVisible();
    await fillShippingAddress(page);

    const bGroup = page.getByRole("group", { name: /E2E 판매자B .* 상품/ });
    const cGroup = page.getByRole("group", { name: /E2E 판매자C .* 상품/ });

    await test.step("b1 쿠폰 창: 최소 35,000원 쿠폰은 비활성", async () => {
      await bGroup.getByRole("button", { name: /^쿠폰 선택/ }).click();
      const dialog = page.getByRole("dialog", { name: "상품 쿠폰 선택" });
      await expect(dialog.getByRole("radio", { name: nMin })).toBeDisabled();
      await expect(dialog.getByText("35,000원 이상 구매 시 사용 가능", { exact: true })).toBeVisible();
      await dialog.getByRole("radio", { name: n2000 }).check();
      await dialog.getByRole("button", { name: "적용하기" }).click();
      await expect(dialog).toBeHidden();
    });

    await test.step("c1 쿠폰 창: b1 에 쓴 2,000원 쿠폰은 다른 상품에 적용됨", async () => {
      await cGroup.getByRole("button", { name: /^쿠폰 선택/ }).click();
      const dialog = page.getByRole("dialog", { name: "상품 쿠폰 선택" });
      await expect(dialog.locator("label").filter({ hasText: n2000 }).getByText("다른 상품에 적용됨", { exact: true })).toBeVisible();
      await dialog.getByRole("radio", { name: nSeller }).check();
      await dialog.getByRole("button", { name: "적용하기" }).click();
      await expect(dialog).toBeHidden();
    });

    await test.step("장바구니 쿠폰 선택 → 할인과 결제 금액", async () => {
      await page.getByRole("region", { name: "할인·포인트" }).getByRole("button", { name: /^쿠폰 선택/ }).click();
      const dialog = page.getByRole("dialog", { name: "장바구니 쿠폰 선택" });
      await dialog.getByRole("radio", { name: nCart }).check();
      await dialog.getByRole("button", { name: "적용하기" }).click();
      await expect(dialog).toBeHidden();

      await expect(valueOf(page, "상품 쿠폰 할인")).toHaveText("−6,000원");
      await expect(valueOf(page, "장바구니 쿠폰 할인")).toHaveText("−5,000원");
      await expect(valueOf(page, "배송비")).toHaveText("0원");
      await expect(page.locator("strong.text-2xl")).toHaveText("59,000원");
      await expect(page.getByRole("button", { name: "59,000원 결제하기" })).toBeEnabled();
    });

    await page.getByRole("button", { name: "59,000원 결제하기" }).click();
    await page.waitForURL((u) => /\/my\/orders\/\d+\/pay$/.test(u.pathname));
    await payWithMock(page);

    const orders = await api.get<{ content: { orderId: number }[] }>(request, "/api/v1/orders", buyer.accessToken);
    expect(orders.content).toHaveLength(1);
    const orderId = orders.content[0]!.orderId;

    await test.step("주문 상세 결제 요약에 같은 할인", async () => {
      await page.goto(`/my/orders/${orderId}`);
      const summary = page.getByRole("region", { name: "결제 요약" });
      await expect(valueOf(summary, "상품 금액")).toHaveText("70,000원");
      await expect(valueOf(summary, "상품 쿠폰 할인")).toHaveText("−6,000원");
      await expect(valueOf(summary, "장바구니 쿠폰 할인")).toHaveText("−5,000원");
      await expect(valueOf(summary, "결제 금액")).toHaveText("59,000원");
    });

    await test.step("내 쿠폰 사용 완료에 세 장", async () => {
      await page.goto("/my/coupons?tab=mine&status=USED");
      for (const n of [n2000, nSeller, nCart]) await expect(page.getByText(n, { exact: true })).toHaveCount(1);
      await expect(page.getByText(nMin, { exact: true })).toHaveCount(0);
    });

    await test.step("c1 항목 취소: 쿠폰 안내 → 판매자 C 쿠폰이 사용 가능으로 돌아온다", async () => {
      await page.goto(`/my/orders/${orderId}`);
      const card = page.getByRole("region", { name: /E2E 판매자C .* 주문/ });
      await card.getByRole("button", { name: "취소", exact: true }).click();
      const dialog = page.getByRole("dialog", { name: "주문 항목 취소" });
      await expect(dialog.getByRole("list", { name: "쿠폰·포인트 안내" }).getByText("사용한 상품 쿠폰은 유효기간이 남아 있으면 돌아옵니다", { exact: true })).toBeVisible();
      await dialog.getByLabel(/취소 사유/).fill("단순 변심");
      await dialog.getByRole("button", { name: "취소하기" }).click();
      await expect(dialog).toBeHidden();

      await page.goto("/my/coupons?tab=mine");
      await expect(page.getByText(nSeller, { exact: true })).toHaveCount(1);
      await expect(page.getByText(n2000, { exact: true })).toHaveCount(0);
    });
  } finally {
    for (const id of policyIds) await endAdminCouponPolicy(request, admin, id);
  }
});

test("0원 결제는 결제창 없이 바로 주문이 완료된다", async ({ page, request }) => {
  const run = uniq();
  const admin = await adminToken(request);
  const d = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자D ${run}`, productName: `E2E 상품D ${run}`, price: 60000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });
  const name = `E2E 6만원 전액 쿠폰 ${run}`;
  const policyId = await createAdminCouponPolicy(request, admin, { name, couponType: "PRODUCT", targetType: "PRODUCT", targetProductId: d.productId, discountType: "FIXED", discountValue: 60000 });
  try {
    await claimCoupon(request, buyer.accessToken, policyId);
    await uiLogin(page, buyer.email, buyer.password);
    await page.waitForURL((u) => u.pathname !== "/login");

    await page.goto(`/checkout?skuId=${d.skuId}&qty=1`);
    await expect(page.getByRole("heading", { name: "주문서" })).toBeVisible();
    await fillShippingAddress(page);
    await page.getByRole("group", { name: /E2E 판매자D .* 상품/ }).getByRole("button", { name: /^쿠폰 선택/ }).click();
    const dialog = page.getByRole("dialog", { name: "상품 쿠폰 선택" });
    await dialog.getByRole("radio", { name }).check();
    await dialog.getByRole("button", { name: "적용하기" }).click();
    await expect(dialog).toBeHidden();

    await expect(page.locator("strong.text-2xl")).toHaveText("0원");
    const order = page.getByRole("button", { name: "주문하기", exact: true });
    await expect(order).toBeEnabled();
    await order.click();

    // 모의 결제창이 뜨지 않고 바로 주문 완료 화면으로 간다.
    await page.waitForURL((u) => /\/my\/orders\/\d+\/complete$/.test(u.pathname));
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "결제 완료" })).toBeVisible();

    const orders = await api.get<{ content: { orderId: number }[] }>(request, "/api/v1/orders", buyer.accessToken);
    await page.goto(`/my/orders/${orders.content[0]!.orderId}`);
    await expect(valueOf(page.getByRole("region", { name: "결제 요약" }), "결제 금액")).toHaveText("0원");
  } finally {
    await endAdminCouponPolicy(request, admin, policyId);
  }
});
