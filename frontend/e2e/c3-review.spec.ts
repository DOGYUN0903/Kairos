import { expect, test } from "@playwright/test";
import { adminToken, api, asUser, createSellerWithProduct, createUser, placePaidOrderApi, requireAdmin, shipAndDeliver, uiLogin, uniq } from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

test("구매 확정 → 후기 작성(사진) → 판매자 답글 → 수정 → 삭제, 그리고 좋아요", async ({ page, request }) => {
  test.setTimeout(240_000);
  const run = uniq();
  const admin = await adminToken(request);
  const productName = `E2E 상품 ${run}`;
  const s = await createSellerWithProduct(request, admin, { sellerName: `E2E 판매자 ${run}`, productName, price: 20000, stock: 10 });
  const buyer = await createUser(request, { name: `E2E 구매자 ${run}` });
  const order = await placePaidOrderApi(request, buyer.accessToken, [{ skuId: s.skuId, quantity: 1 }]);
  await shipAndDeliver(request, s.sellerToken, buyer.accessToken, order);

  await uiLogin(page, buyer.email, buyer.password);
  await page.waitForURL((u) => u.pathname !== "/login");

  const reviewText = "상품이 마음에 들어요 정말 좋습니다";
  const editedText = "한 달 써 보니 더욱 마음에 듭니다 추천해요";
  const replyText = "구매해 주셔서 감사합니다 또 이용해 주세요";

  await test.step("화면에서 구매 확정(확인 창에 1% 안내)", async () => {
    await page.goto(`/my/orders/${order.orderId}`);
    const card = page.getByRole("region", { name: /E2E 판매자 .* 주문/ });
    await card.getByRole("button", { name: "구매 확정" }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByText("확정하면 되돌릴 수 없습니다. 구매 확정 시 결제 금액의 1%가 포인트로 적립됩니다.", { exact: true })).toBeVisible();
    await dialog.getByRole("button", { name: "구매 확정" }).click();
    await expect(card.getByRole("button", { name: "구매 확정" })).toHaveCount(0);
    await expect(card.getByRole("button", { name: "후기 작성" })).toBeVisible();
    await expect(card.getByText(/까지 작성 가능/)).toBeVisible();
  });

  await test.step("후기 작성: 9자는 제출되지 않고 10자부터 사진 포함으로 등록된다", async () => {
    await page.getByRole("button", { name: "후기 작성" }).click();
    const dialog = page.getByRole("dialog", { name: "후기 작성" });
    await dialog.getByRole("radio", { name: "5점" }).click();
    await dialog.getByLabel(/^후기 내용/).fill("가나다라마바사아자");
    await dialog.getByRole("button", { name: "후기 등록" }).click();
    await expect(dialog.getByText("10자 이상 입력해 주세요", { exact: true })).toBeVisible();
    await expect(dialog).toBeVisible();

    await dialog.getByLabel(/^후기 내용/).fill(reviewText);
    await dialog.locator('input[type="file"]').setInputFiles(`${__dirname}/assets/sample.webp`);
    await expect(dialog.getByRole("img", { name: "후기 사진 1" })).toBeVisible();
    await dialog.getByRole("button", { name: "후기 등록" }).click();
    await expect(page.getByText("후기를 등록했습니다. 500P가 적립되었습니다.", { exact: true }).first()).toBeVisible();
    await expect(dialog).toBeHidden();
    await expect(page.getByRole("link", { name: "내 후기 보기" })).toBeVisible();
  });

  await test.step("상품 상세: 후기 1개, 평균 5.0", async () => {
    await page.goto(`/products/${s.productId}`);
    const section = page.locator("#reviews");
    await expect(section.getByText("후기 1개", { exact: true })).toBeVisible();
    await expect(section.getByText("5.0", { exact: true })).toBeVisible();
    await expect(section.getByText(reviewText, { exact: true })).toBeVisible();
  });

  await test.step("판매자: 미답변 → 답글 등록", async () => {
    await asUser(page, s.sellerEmail, "e2e-password-1", async (sp) => {
      await sp.goto("/seller/reviews");
      const row = sp.getByRole("row").filter({ hasText: productName });
      await expect(row.getByText("미답변", { exact: true })).toBeVisible();
      await row.getByRole("button", { name: "답글 달기" }).click();
      await sp.getByLabel("답글 작성").fill(replyText);
      await sp.getByRole("button", { name: "답글 등록" }).click();
      await expect(sp.getByRole("row").filter({ hasText: productName }).getByText("답변 완료", { exact: true })).toBeVisible();
    });
    await page.goto(`/products/${s.productId}`);
    await expect(page.locator("#reviews").getByText(replyText, { exact: true })).toBeVisible();
  });

  await test.step("구매자: 본문만 수정(사진 1장 유지)", async () => {
    await page.goto("/my/reviews");
    await page.getByRole("button", { name: "수정", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "후기 수정" });
    await expect(dialog.getByRole("img", { name: "후기 사진 1" })).toBeVisible();
    await expect(dialog.getByRole("img")).toHaveCount(1);
    await dialog.getByLabel(/^후기 내용/).fill(editedText);
    await dialog.getByRole("button", { name: "수정 완료" }).click();
    await expect(dialog).toBeHidden();
    const card = page.getByRole("article").filter({ hasText: productName });
    await expect(card.getByText(editedText, { exact: true })).toBeVisible();
    await expect(card.getByText("수정됨", { exact: true })).toBeVisible();
    await expect(card.getByRole("button", { name: /^사진 \d+ 크게 보기$/ })).toHaveCount(1);
  });

  await test.step("삭제 → 다시 쓸 수 없고 상품 상세에 후기가 없다", async () => {
    await page.getByRole("button", { name: "삭제", exact: true }).click();
    await page.getByRole("dialog", { name: "후기 삭제" }).getByRole("button", { name: "삭제", exact: true }).click();
    await expect(page.getByText("작성한 후기가 없습니다", { exact: true })).toBeVisible();

    await page.goto(`/my/orders/${order.orderId}`);
    await expect(page.getByText("삭제한 후기는 다시 작성할 수 없습니다", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "후기 작성" })).toHaveCount(0);

    await page.goto(`/products/${s.productId}`);
    await expect(page.locator("#reviews").getByText("아직 후기가 없습니다", { exact: true })).toBeVisible();
  });

  await test.step("좋아요: 상품 상세에서 누르면 내 좋아요에 카드, 카드 하트로 취소하면 빈 상태", async () => {
    await page.goto(`/products/${s.productId}`);
    const like = page.getByRole("button", { name: /^좋아요/ });
    await like.click();
    await expect(like).toHaveAttribute("aria-pressed", "true");

    await page.goto("/my/likes");
    const cardLink = page.getByRole("link", { name: new RegExp(productName) }).first();
    await expect(cardLink).toBeVisible();
    await page.getByRole("button", { name: "좋아요", exact: true }).click();
    await expect(page.getByText("좋아요한 상품이 없습니다", { exact: true })).toBeVisible();

    const liked = await api.get<{ content: unknown[] }>(request, "/api/v1/products/me/likes", buyer.accessToken);
    expect(liked.content).toHaveLength(0);
  });
});
