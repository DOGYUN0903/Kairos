import path from "node:path";
import { expect, test, type Page } from "@playwright/test";
import {
  adminToken,
  api,
  approveBrand,
  brandIdByName,
  createUser,
  leafCategory,
  rejectChangeRequest,
  requireAdmin,
  sellerId as getSellerId,
  suspendSeller,
  uiLogin,
  uniq,
  ADMIN_EMAIL,
  DEMO_PASSWORD,
} from "./fixtures/api";

const IMAGES = [path.join(__dirname, "assets/sample.webp"), path.join(__dirname, "assets/sample-2.webp")];

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

async function asAdmin(page: Page, fn: (admin: Page) => Promise<void>) {
  const ctx = await page.context().browser()!.newContext({ viewport: { width: 1440, height: 900 }, baseURL: test.info().project.use.baseURL });
  const admin = await ctx.newPage();
  await uiLogin(admin, ADMIN_EMAIL, DEMO_PASSWORD);
  await admin.waitForURL((u) => u.pathname === "/admin");
  try {
    await fn(admin);
  } finally {
    await ctx.close();
  }
}

/** 판매자가 상품 상세에서 SKU 행의 재고를 저장한다. */
async function setStock(page: Page, sku: string, stock: string) {
  const input = page.getByLabel(`${sku} 재고`);
  await input.fill(stock);
  const row = page.getByRole("row").filter({ has: input });
  await row.getByRole("button", { name: "저장" }).click();
  await expect(row.getByRole("button", { name: "저장" })).toBeDisabled();
  await expect(page.getByLabel(`${sku} 재고`)).toHaveValue(stock);
}

test("판매자 입점 → 관리자 승인(키보드) → 브랜드 → 상품 등록(이미지 2장·옵션 2축, 키보드 제출) → 상품 승인 → 구매자 노출 → 품절 → 일시 중지", async ({ page, request }) => {
  test.setTimeout(240_000);
  const run = uniq();
  const company = `E2E 상사 ${run}`;
  const brandName = `E2E 브랜드 ${run}`;
  const productName = `E2E 셔츠 ${run}`;
  const seller = await createUser(request, { name: `E2E 판매자 ${run}` });
  const admin = await adminToken(request);

  await test.step("입점 신청(화면)", async () => {
    await uiLogin(page, seller.email, seller.password, "/seller");
    await page.waitForURL((u) => u.pathname === "/seller");
    await page.getByRole("link", { name: "입점 신청" }).click();
    await page.getByLabel("회사명").fill(company);
    await page.getByLabel("사업자등록번호").fill(String(Math.floor(1e9 + Math.random() * 9e9)));
    await page.getByLabel("대표자").fill("홍길동");
    await page.getByLabel("연락처").fill("010-1234-5678");
    await page.getByLabel("정산 은행").selectOption("신한은행");
    await page.getByLabel("계좌번호").fill("110123456789");
    await page.getByLabel("예금주").fill("홍길동");
    await page.getByRole("button", { name: "신청하기" }).click();
    await page.waitForURL((u) => u.pathname === "/seller");
    await expect(page.getByText("심사 중입니다.")).toBeVisible();
  });
  const sellerId = await getSellerId(request, seller.accessToken);

  await test.step("관리자 판매자 승인(화면, 키보드만)", async () => {
    await asAdmin(page, async (a) => {
      await a.goto(`/admin/review?tab=sellers&sort=LATEST&id=${sellerId}`);
      const approve = a.getByRole("button", { name: "승인", exact: true });
      await expect(approve).toBeEnabled();
      await approve.focus();
      await a.keyboard.press("Enter");
      const bp = a.getByLabel("수수료율 (bp)");
      await expect(bp).toBeFocused();
      await a.keyboard.press("ControlOrMeta+a");
      await a.keyboard.type("1000");
      await expect(a.getByText("수수료율 10%")).toBeVisible();
      await a.keyboard.press("Enter");
      await expect(a.getByRole("dialog")).toBeHidden();
    });
    const me = await api.get<{ status: string; commissionRateBp: number }>(request, "/api/v1/seller/me", seller.accessToken);
    expect(me.status).toBe("APPROVED");
  });

  await test.step("브랜드 신청(화면) → 승인(API)", async () => {
    await page.goto("/seller/brands");
    await page.getByRole("button", { name: "브랜드 신청" }).click();
    await page.getByLabel("브랜드명").fill(brandName);
    await page.getByRole("dialog").getByRole("button", { name: "신청하기" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(page.getByText(brandName)).toBeVisible();
    await approveBrand(request, admin, await brandIdByName(request, seller.accessToken, brandName));
  });

  let productId = 0;
  await test.step("상품 등록(화면, 이미지 2장·색상 2×사이즈 2, 키보드로 제출)", async () => {
    const { parentId, leafId } = await leafCategory(request);
    await page.goto("/seller/products/new");
    await page.getByLabel("브랜드").selectOption({ label: brandName });
    await page.getByLabel("대분류").selectOption(String(parentId));
    await page.getByLabel("소분류").selectOption(String(leafId));
    await page.getByLabel("상품명").fill(productName);
    await page.getByLabel("상품 설명").fill("E2E 로 등록한 셔츠입니다.");
    await page.getByLabel("이미지 추가").setInputFiles(IMAGES);
    await expect(page.getByRole("list", { name: "이미지 추가 목록" }).getByRole("listitem")).toHaveCount(2);
    await expect(page.getByLabel("업로드 중")).toHaveCount(0);
    await page.getByLabel("정가").fill("50000");
    await page.getByLabel("판매가").fill("39000");
    await page.getByRole("button", { name: "+ 옵션 축 추가" }).click();
    await page.getByLabel("옵션 1", { exact: true }).fill("색상");
    for (const v of ["블랙", "화이트"]) {
      await page.getByLabel("색상 값 추가").fill(v);
      await page.getByLabel("색상 값 추가").press("Enter");
    }
    await page.getByRole("button", { name: "+ 옵션 축 추가" }).click();
    await page.getByLabel("옵션 2", { exact: true }).fill("사이즈");
    for (const v of ["S", "M"]) {
      await page.getByLabel("사이즈 값 추가").fill(v);
      await page.getByLabel("사이즈 값 추가").press("Enter");
    }
    // 키보드로 제출 버튼까지 가서 Enter.
    const submit = page.getByRole("button", { name: "검수 요청" });
    await page.getByLabel("사이즈 값 추가").focus();
    for (let i = 0; i < 40 && !(await submit.evaluate((el) => el === document.activeElement)); i++) await page.keyboard.press("Tab");
    await expect(submit).toBeFocused();
    const created = page.waitForResponse((r) => r.url().endsWith("/api/v1/seller/products") && r.request().method() === "POST");
    await page.keyboard.press("Enter");
    productId = ((await (await created).json()) as { data: { productId: number } }).data.productId;
    await page.waitForURL(`**/seller/products/${productId}`);
    await expect(page.getByRole("rowheader")).toHaveCount(4);
  });

  await test.step("관리자 상품 승인(화면)", async () => {
    await asAdmin(page, async (a) => {
      await a.goto(`/admin/review?tab=products&sort=LATEST&id=${productId}`);
      await a.getByRole("button", { name: "승인", exact: true }).click();
      await a.getByRole("dialog").getByRole("button", { name: "승인" }).click();
      await expect(a.getByRole("dialog")).toBeHidden();
    });
  });

  await test.step("판매자 재고 입력", async () => {
    await page.reload();
    for (const sku of ["블랙 / S", "블랙 / M", "화이트 / S", "화이트 / M"]) await setStock(page, sku, "5");
  });

  await test.step("구매자: 검색으로 보이고 상세에서 옵션 선택 합계", async () => {
    await page.goto(`/products?keyword=${encodeURIComponent(productName)}`);
    await page.getByRole("link", { name: new RegExp(productName) }).first().click();
    await page.waitForURL(`**/products/${productId}`);
    await page.getByRole("group", { name: "색상" }).getByRole("button", { name: "화이트" }).click();
    await page.getByRole("group", { name: "사이즈" }).getByRole("button", { name: "M" }).click();
    await expect(page.getByText("39,000원").last()).toBeVisible();
    await expect(page.getByText("총 상품 금액")).toBeVisible();
  });

  await test.step("재고 0 → 구매자 품절", async () => {
    await page.goto(`/seller/products/${productId}`);
    for (const sku of ["블랙 / S", "블랙 / M", "화이트 / S", "화이트 / M"]) await setStock(page, sku, "0");
    await page.goto(`/products/${productId}`);
    await expect(page.getByText("품절된 상품입니다.")).toBeVisible();
  });

  await test.step("일시 중지 → 목록에서 사라짐", async () => {
    await page.goto(`/seller/products/${productId}`);
    await page.getByRole("button", { name: "일시 중지" }).click();
    await expect(page.getByRole("button", { name: "판매 재개" })).toBeVisible();
    await page.goto(`/products?keyword=${encodeURIComponent(productName)}`);
    await expect(page.getByText("조건에 맞는 상품이 없습니다").or(page.getByText(/없습니다/)).first()).toBeVisible();
    await expect(page.getByRole("link", { name: new RegExp(productName) })).toHaveCount(0);
  });

  await test.step("판매 재개 후 변경 요청: 옵션 값 이름 변경은 SKU 재고를 유지한다", async () => {
    await page.goto(`/seller/products/${productId}`);
    await page.getByRole("button", { name: "판매 재개" }).click();
    await expect(page.getByRole("button", { name: "일시 중지" })).toBeVisible();
    await setStock(page, "블랙 / S", "7");
    await page.getByRole("link", { name: "내용 수정(재검수)" }).click();
    await page.getByRole("button", { name: "블랙 이름 변경" }).click();
    await page.getByLabel("블랙 새 이름").fill("차콜");
    await page.getByLabel("블랙 새 이름").press("Enter");
    const sent = page.waitForResponse((r) => r.url().includes("/change-requests") && r.request().method() === "POST");
    await page.getByRole("button", { name: "변경 요청" }).click();
    expect((await sent).ok()).toBeTruthy();
    await page.waitForURL(`**/seller/products/${productId}`);
    await expect(page.getByText(/검수 중 · #CR-/)).toBeVisible();

    await asAdmin(page, async (a) => {
      await a.goto("/admin/review?tab=change-requests&sort=LATEST");
      const row = a.getByRole("row").filter({ hasText: productName });
      await expect(row.getByText("옵션", { exact: true })).toBeVisible();
      await row.getByRole("button", { name: `${productName} 열기` }).click();
      await a.getByRole("button", { name: "승인", exact: true }).click();
      await a.getByRole("dialog").getByRole("button", { name: "승인" }).click();
      await expect(a.getByRole("dialog")).toBeHidden();
    });

    await page.reload();
    await expect(page.getByLabel("차콜 / S 재고")).toHaveValue("7");
    await expect(page.getByLabel("차콜 / M 재고")).toHaveValue("0");
    await expect(page.getByRole("button", { name: /단종/ })).toHaveCount(0);
  });

  await test.step("변경 요청 반려 사유가 판매자 상세에 보인다", async () => {
    await page.getByRole("link", { name: "내용 수정(재검수)" }).click();
    await page.getByLabel("상품명").fill(`${productName} v2`);
    await page.getByRole("button", { name: "변경 요청" }).click();
    await page.waitForURL(`**/seller/products/${productId}`);
    await expect(page.getByText(/검수 중 · #CR-/)).toBeVisible();
    const crText = (await page.getByText(/검수 중 · #CR-/).textContent()) ?? "";
    const crId = Number(/#CR-(\d+)/.exec(crText)?.[1]);
    await rejectChangeRequest(request, admin, crId, "E2E 반려 사유: 상품명 규칙 위반");
    await page.reload();
    await expect(page.getByText(/반려됨 · #CR-/)).toBeVisible();
    await expect(page.getByText("반려 사유: E2E 반려 사유: 상품명 규칙 위반")).toBeVisible();
  });

  await test.step("정지된 판매자는 조회만 할 수 있다", async () => {
    await suspendSeller(request, admin, sellerId, "E2E 정지");
    await page.reload();
    await expect(page.getByText("판매자 정지 중:")).toBeVisible();
    await expect(page.getByText("정지 중에는 조회와 일시 중지·종료만 할 수 있습니다.")).toBeVisible();
    await expect(page.getByLabel("차콜 / S 재고")).toBeDisabled();
    await expect(page.getByRole("button", { name: "다시 수정(재검수)" })).toBeDisabled();
    await page.goto("/seller/brands");
    await expect(page.getByRole("button", { name: "브랜드 신청" })).toBeDisabled();
  });
});
