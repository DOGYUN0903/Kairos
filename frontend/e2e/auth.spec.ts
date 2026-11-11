import { expect, test } from "@playwright/test";
import { PASSWORD, createUser, uiLogin, uniq } from "./fixtures/api";

test("가입 → 이름 표시·새로고침 유지 → 로그아웃 → 보호 화면은 로그인 뒤 복귀, 관리자 화면은 거부", async ({ page }) => {
  const name = `E2E 회원 ${uniq()}`;
  const email = `e2e-${Date.now()}-${uniq()}@kairos.test`;

  await page.goto("/signup");
  await page.getByLabel("이메일").fill(email);
  await page.getByLabel("비밀번호").fill(PASSWORD);
  await page.getByLabel("이름").fill(name);
  await page.getByRole("button", { name: "가입하기" }).click();
  await page.waitForURL((u) => u.pathname === "/");
  await expect(page.getByRole("button", { name: "로그아웃" })).toBeVisible();

  await page.getByRole("link", { name: "판매자 센터" }).click();
  await page.waitForURL("**/seller");
  await expect(page.getByRole("banner").getByText(name)).toBeVisible();
  await page.reload();
  await expect(page.getByRole("banner").getByText(name)).toBeVisible();

  await page.getByRole("button", { name: "로그아웃" }).click();
  await page.goto("/");
  await expect(page.getByRole("link", { name: "로그인" })).toBeVisible();

  // 클라이언트 가드가 /login?next=%2Fseller 로 보낸다.
  await page.goto("/seller");
  await page.waitForURL("**/login?next=%2Fseller");
  await page.getByLabel("이메일").fill(email);
  await page.getByLabel("비밀번호").fill(PASSWORD);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await page.waitForURL((u) => u.pathname === "/seller");

  await page.goto("/admin");
  await page.waitForURL("**/forbidden");
});

test("틀린 비밀번호는 안내 문구를 보이고 머문다", async ({ page, request }) => {
  const user = await createUser(request, { name: `E2E ${uniq()}` });
  await uiLogin(page, user.email, "wrong-password-0");
  await expect(page.getByText("이메일 또는 비밀번호가 올바르지 않습니다.")).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test("next 가 외부 주소면 따라가지 않는다(오픈 리다이렉트 방지)", async ({ page, request }) => {
  const user = await createUser(request, { name: `E2E ${uniq()}` });
  for (const next of ["//evil.example.com/x", "https://evil.example.com", "/\\evil.example.com"]) {
    await page.context().clearCookies();
    await uiLogin(page, user.email, user.password, next);
    await page.waitForURL((u) => u.pathname === "/");
    expect(page.url()).not.toContain("evil");
  }
});
