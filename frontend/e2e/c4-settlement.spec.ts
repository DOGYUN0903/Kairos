import { expect, test, type Locator, type Page } from "@playwright/test";
import { ADMIN_EMAIL, DEMO_PASSWORD, DEMO_SELLER_EMAIL, asUser, loginToken, lastWeekMondayKst, requireAdmin, sellerId, sundayOf, uiLogin } from "./fixtures/api";

// 새 스키마에서 한 번만 돈다(같은 스키마에서 다시 돌리면 그 기간은 "이미 생성된 기간"으로 나온다). 한국 시간 월요일 00:00 을 넘겨 돌리지 않는다(지난주 기준이 바뀐다).
test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

/** 계산 내역(8줄)의 한 줄 값. 줄 앞 부호 칸과 라벨은 따로 있고, 금액 글자는 마지막 칸이다. */
function breakdownValue(page: Page, label: string): Locator {
  return page.getByRole("region", { name: "계산 내역" }).locator("li").filter({ hasText: label }).locator(":scope > *").last();
}

/** 포함된 주문 표의 라인 금액 칸(마지막 열). 줄 순서는 정해져 있지 않아 정렬해서 비교한다. */
async function lineAmounts(page: Page): Promise<string[]> {
  const cells = page.getByRole("region", { name: "포함된 주문" }).locator("tbody tr td:last-child");
  await expect(cells).toHaveCount(3);
  return (await cells.allTextContents()).map((s) => s.trim()).sort();
}

const LINE_AMOUNTS = ["70,500원", "13,500원", "−3,000원"].sort();

test("정산 생성 → 확정 → 지급 완료 → 재생성 안내 → 조정 등록·삭제 → 판매자 화면까지 이어진다", async ({ page, request }) => {
  test.setTimeout(120_000);
  const monday = lastWeekMondayKst();
  const sunday = sundayOf(monday);
  const period = `${monday} ~ ${sunday}`;
  const demoSellerId = await sellerId(request, await loginToken(request, DEMO_SELLER_EMAIL, DEMO_PASSWORD));

  await uiLogin(page, ADMIN_EMAIL, DEMO_PASSWORD, "/admin/settlements");
  await page.waitForURL("**/admin/settlements");

  await test.step("정산 생성: 기본 날짜는 지난주 월요일, 연타해도 결과는 생성 1", async () => {
    await page.getByRole("button", { name: "정산 생성", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "주간 정산 생성" });
    await expect(dialog.getByLabel("기간 시작일 (월요일)")).toHaveValue(monday);
    // 연타해도 생성 요청은 한 번만 나간다(결과가 "생성 1"인 것은 서버가 중복을 막아도 같아서, 실제로 나간 POST 를 센다).
    const generatePosts: string[] = [];
    page.on("request", (req) => {
      if (req.method() === "POST" && new URL(req.url()).pathname === "/api/v1/admin/settlements/generate") generatePosts.push(req.url());
    });
    await dialog.getByRole("button", { name: "정산 생성", exact: true }).click({ clickCount: 2, delay: 0 });

    const result = page.getByRole("dialog", { name: "정산 생성 결과" });
    await expect(result.getByText(period, { exact: true })).toBeVisible();
    await expect(result.getByText("생성", { exact: true }).locator("xpath=preceding-sibling::strong")).toHaveText("1");
    await expect(result.getByText("건너뜀", { exact: true }).locator("xpath=preceding-sibling::strong")).toHaveText("0");
    await expect(result.getByText("실패", { exact: true }).locator("xpath=preceding-sibling::strong")).toHaveText("0");
    await expect(result.getByText("대상 판매자 1명", { exact: true }).first()).toBeVisible();
    await result.getByRole("button", { name: "목록 보기" }).click();
    await expect(result).toBeHidden();
    await page.waitForTimeout(500); // 늦게 나가는 두 번째 요청이 있다면 잡히도록 잠깐 기다린다(관리자 화면은 networkidle 에 도달하지 않을 수 있다)
    expect(generatePosts).toHaveLength(1);
  });

  await test.step("목록에서 데모 판매자 정산 → 상세 금액", async () => {
    await page.goto(`/admin/settlements?sellerId=${demoSellerId}`);
    await page.getByRole("link", { name: period, exact: true }).click();
    await page.waitForURL(/\/admin\/settlements\/\d+$/);

    await expect(breakdownValue(page, "상품 매출")).toHaveText("95,000원");
    await expect(breakdownValue(page, "판매자 쿠폰 할인")).toHaveText("−5,000원");
    await expect(breakdownValue(page, "수수료")).toHaveText("−9,000원");
    await expect(breakdownValue(page, "배송비")).toHaveText("+6,000원");
    await expect(breakdownValue(page, "판매자 부담 반품비")).toHaveText("−6,000원");
    await expect(breakdownValue(page, "합계")).toHaveText("81,000원");
    await expect(breakdownValue(page, "지급액")).toHaveText("81,000원");
    expect(await lineAmounts(page)).toEqual(LINE_AMOUNTS);
  });

  const heading = page.getByRole("heading", { level: 2 }).filter({ hasText: /^정산 / });

  await test.step("확정 → 지급 완료(메모)", async () => {
    await page.getByRole("button", { name: "정산 확정", exact: true }).click();
    const confirm = page.getByRole("dialog", { name: "정산 확정" });
    await confirm.getByRole("button", { name: "확정", exact: true }).click();
    await expect(confirm).toBeHidden();
    await expect(heading.getByText("확정", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: "지급 완료", exact: true }).click();
    const pay = page.getByRole("dialog", { name: "지급 완료 처리" });
    await pay.getByLabel(/^메모/).fill("E2E 지급");
    await pay.getByRole("button", { name: "지급 완료", exact: true }).click();
    await expect(pay).toBeHidden();
    await expect(heading.getByText("지급 완료", { exact: true })).toBeVisible();
    await expect(page.getByRole("region", { name: "정산 정보" }).getByText("E2E 지급", { exact: true })).toBeVisible();
  });

  await test.step("같은 기간을 다시 생성하면 이미 생성된 기간이다", async () => {
    await page.goto("/admin/settlements");
    await page.getByRole("button", { name: "정산 생성", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "주간 정산 생성" });
    await expect(dialog.getByLabel("기간 시작일 (월요일)")).toHaveValue(monday);
    await dialog.getByRole("button", { name: "정산 생성", exact: true }).click();
    const result = page.getByRole("dialog", { name: "정산 생성 결과" });
    await expect(result.getByText("이미 생성된 기간입니다", { exact: true })).toBeVisible();
    await result.getByRole("button", { name: "닫기" }).click();
    await expect(result).toBeHidden();
  });

  await test.step("조정 −10,000원 등록 → 다음 정산에 반영 예정 → 삭제", async () => {
    await page.goto(`/admin/settlements/adjustments?sellerId=${demoSellerId}`);
    await page.getByRole("button", { name: "조정 등록" }).click();
    const form = page.getByRole("region", { name: "조정 등록" });
    await form.getByRole("radio", { name: "차감" }).check();
    await form.getByLabel(/^금액/).fill("10000");
    await form.getByLabel(/^사유/).fill("E2E 차감 조정");
    await form.getByRole("button", { name: "등록", exact: true }).click();

    const list = page.getByRole("region", { name: "조정 목록" });
    const row = list.getByRole("row").filter({ hasText: "E2E 차감 조정" });
    await expect(row.getByRole("cell", { name: "−10,000원", exact: true })).toBeVisible();
    await expect(row.getByText("다음 정산에 반영 예정", { exact: true })).toBeVisible();

    await row.getByRole("button", { name: "삭제" }).click();
    const del = page.getByRole("dialog", { name: "조정 삭제" });
    await del.getByRole("button", { name: "삭제", exact: true }).click();
    await expect(del).toBeHidden();
    await expect(list.getByRole("row").filter({ hasText: "E2E 차감 조정" })).toHaveCount(0);
    await expect(list.getByText("E2E 차감 조정")).toHaveCount(0);
  });

  await asUser(page, DEMO_SELLER_EMAIL, DEMO_PASSWORD, async (sp) => {
    await test.step("판매자: 정산 목록의 지급액·지급 완료, 상세의 라인 3줄", async () => {
      await sp.goto("/seller/settlements");
      const row = sp.getByRole("row").filter({ hasText: period });
      await expect(row.getByText("지급 완료", { exact: true })).toBeVisible();
      // 지급액은 "지급액" 머리글이 있는 열의 칸이다(합계 열과 같은 금액이라 열 위치로 구분한다).
      const payableCol = (await sp.getByRole("columnheader").allTextContents()).findIndex((t) => t.trim() === "지급액");
      expect(payableCol).toBeGreaterThanOrEqual(0);
      await expect(row.getByRole("cell").nth(payableCol)).toHaveText("81,000원");
      await expect(row.getByText("지급 완료", { exact: true })).toBeVisible();
      await row.getByRole("link", { name: period, exact: true }).click();
      await sp.waitForURL(/\/seller\/settlements\/\d+$/);
      await expect(breakdownValue(sp, "합계")).toHaveText("81,000원");
      expect(await lineAmounts(sp)).toEqual(LINE_AMOUNTS);
    });
  });
});
