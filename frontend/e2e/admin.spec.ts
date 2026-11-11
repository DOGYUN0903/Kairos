import { expect, test } from "@playwright/test";
import { ADMIN_EMAIL, DEMO_PASSWORD, adminToken, createCategory, deleteCategory, requireAdmin, uiLogin, uniq } from "./fixtures/api";

test.skip(() => requireAdmin() !== null, requireAdmin() ?? "");

test("카테고리 소분류 순서 변경이 새로고침 뒤에도 유지된다", async ({ page, request }) => {
  const admin = await adminToken(request);
  const run = uniq();
  const rootName = `E2E 대분류 ${run}`;
  const rootId = await createCategory(request, admin, { parentId: null, name: rootName, displayOrder: 900 });
  const a = await createCategory(request, admin, { parentId: rootId, name: `E2E 가 ${run}`, displayOrder: 1 });
  const b = await createCategory(request, admin, { parentId: rootId, name: `E2E 나 ${run}`, displayOrder: 2 });
  try {
    await uiLogin(page, ADMIN_EMAIL, DEMO_PASSWORD, "/admin/categories");
    await page.waitForURL("**/admin/categories");
    const children = page.getByRole("list", { name: `${rootName} 소분류` });
    await expect(children.getByRole("listitem")).toHaveText([new RegExp(`E2E 가 ${run}`), new RegExp(`E2E 나 ${run}`)]);
    await page.getByRole("button", { name: `E2E 나 ${run} 위로` }).click();
    await expect(children.getByRole("listitem")).toHaveText([new RegExp(`E2E 나 ${run}`), new RegExp(`E2E 가 ${run}`)]);
    await page.reload();
    await expect(page.getByRole("list", { name: `${rootName} 소분류` }).getByRole("listitem")).toHaveText([
      new RegExp(`E2E 나 ${run}`),
      new RegExp(`E2E 가 ${run}`),
    ]);
  } finally {
    await deleteCategory(request, admin, a);
    await deleteCategory(request, admin, b);
    await deleteCategory(request, admin, rootId);
  }
});
