import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { basename, join } from "node:path";
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/** 백엔드를 직접 부른다(Bearer). 화면이 검증 대상이 아닌 준비·정리 단계에만 쓴다. */
export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
export const DEMO_PASSWORD = process.env.KAIROS_DEMO_PASSWORD ?? "";
export const ADMIN_EMAIL = "admin@kairos.demo";
export const PASSWORD = "e2e-password-1";

/** 실행마다 겹치지 않는 접미사. 재시드 상태에 기대지 않으려고 모든 데이터 이름에 붙인다. */
export function uniq(): string {
  return `${Date.now().toString(36)}${Math.floor(Math.random() * 1e6).toString(36)}`;
}

export function randomBusinessNumber(): string {
  const d = Array.from({ length: 10 }, () => Math.floor(Math.random() * 10)).join("");
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`;
}

async function call<T = unknown>(
  request: APIRequestContext,
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE",
  path: string,
  token: string | null,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = { "Idempotency-Key": randomUUID() };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await request.fetch(`${BACKEND_URL}${path}`, { method, headers, ...(body === undefined ? {} : { data: body }) });
  const text = await res.text();
  expect(res.ok(), `${method} ${path} → ${res.status()} ${text}`).toBeTruthy();
  return (text ? (JSON.parse(text) as { data: T }).data : undefined) as T;
}

export const api = {
  get: <T>(r: APIRequestContext, path: string, token: string | null) => call<T>(r, "GET", path, token),
  post: <T>(r: APIRequestContext, path: string, token: string | null, body?: unknown) => call<T>(r, "POST", path, token, body),
  patch: <T>(r: APIRequestContext, path: string, token: string | null, body?: unknown) => call<T>(r, "PATCH", path, token, body),
  del: (r: APIRequestContext, path: string, token: string | null) => call(r, "DELETE", path, token),
};

export async function loginToken(request: APIRequestContext, email: string, password: string): Promise<string> {
  const data = await api.post<{ accessToken: string }>(request, "/api/v1/auth/login", null, { email, password });
  return data.accessToken;
}

export async function createUser(request: APIRequestContext, { name }: { name: string }) {
  const email = `e2e-${Date.now()}-${uniq()}@kairos.test`;
  await api.post(request, "/api/v1/auth/signup", null, { email, password: PASSWORD, name });
  const accessToken = await loginToken(request, email, PASSWORD);
  return { email, password: PASSWORD, accessToken };
}

export async function adminToken(request: APIRequestContext): Promise<string> {
  return loginToken(request, ADMIN_EMAIL, DEMO_PASSWORD);
}

export async function applySeller(request: APIRequestContext, token: string, companyName: string, businessNumber = randomBusinessNumber()) {
  const { sellerId } = await api.post<{ sellerId: number }>(request, "/api/v1/seller/application", token, {
    companyName,
    businessNumber,
    representativeName: "홍길동",
    phone: "010-0000-0000",
    bank: "국민은행",
    accountNumber: "123456789012",
    holder: "홍길동",
  });
  return sellerId;
}

export async function approveSeller(request: APIRequestContext, admin: string, sellerId: number, bp = 1000) {
  await api.post(request, `/api/v1/admin/sellers/${sellerId}/approve`, admin, { commissionRateBp: bp });
}

export async function suspendSeller(request: APIRequestContext, admin: string, sellerId: number, reason: string) {
  await api.post(request, `/api/v1/admin/sellers/${sellerId}/suspend`, admin, { reason });
}

export async function sellerId(request: APIRequestContext, token: string): Promise<number> {
  return (await api.get<{ sellerId: number }>(request, "/api/v1/seller/me", token)).sellerId;
}

export async function brandIdByName(request: APIRequestContext, token: string, name: string): Promise<number> {
  const brands = await api.get<{ brandId: number; name: string }[]>(request, "/api/v1/seller/brands", token);
  const b = brands.find((x) => x.name === name);
  if (!b) throw new Error(`브랜드 ${name} 이 없습니다`);
  return b.brandId;
}

export async function approveBrand(request: APIRequestContext, admin: string, brandId: number) {
  await api.post(request, `/api/v1/admin/brands/${brandId}/approve`, admin);
}

export async function createApprovedBrand(request: APIRequestContext, token: string, admin: string, name = `E2E 브랜드 ${uniq()}`) {
  const { brandId } = await api.post<{ brandId: number }>(request, "/api/v1/seller/brands", token, { name });
  await approveBrand(request, admin, brandId);
  return { brandId, name };
}

export async function uploadImage(request: APIRequestContext, token: string, path: string): Promise<number> {
  const res = await request.post(`${BACKEND_URL}/api/v1/files/images`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: { file: { name: basename(path), mimeType: "image/webp", buffer: readFileSync(path) } },
  });
  expect(res.ok(), `upload → ${res.status()}`).toBeTruthy();
  return ((await res.json()) as { data: { fileId: number } }).data.fileId;
}

type CategoryNode = { categoryId: number; name: string; children: CategoryNode[] };

/** 소분류가 있는 첫 대분류와 그 첫 소분류. */
export async function leafCategory(request: APIRequestContext): Promise<{ parentId: number; parentName: string; leafId: number }> {
  const tree = await api.get<CategoryNode[]>(request, "/api/v1/categories", null);
  const root = tree.find((t) => t.children.length > 0);
  if (!root || !root.children[0]) throw new Error("소분류가 있는 카테고리가 없습니다");
  return { parentId: root.categoryId, parentName: root.name, leafId: root.children[0].categoryId };
}

export async function leafCategoryId(request: APIRequestContext): Promise<number> {
  return (await leafCategory(request)).leafId;
}

type ProductOptions = { name: string; values: { name: string }[] }[];

const DEFAULT_OPTIONS: ProductOptions = [
  { name: "색상", values: [{ name: "블랙" }, { name: "화이트" }] },
  { name: "사이즈", values: [{ name: "S" }, { name: "M" }] },
];

/** 상품을 API 로 등록한다. 기본은 옵션 색상(블랙·화이트) × 사이즈(S·M), options 에 [] 를 주면 옵션 없는 상품이다. */
export async function registerProduct(
  request: APIRequestContext,
  token: string,
  {
    brandId,
    name,
    imageFileIds,
    categoryId,
    originalPrice = 50000,
    salePrice = 39000,
    options = DEFAULT_OPTIONS,
  }: { brandId: number; name: string; imageFileIds: number[]; categoryId: number; originalPrice?: number; salePrice?: number; options?: ProductOptions },
): Promise<number> {
  const { productId } = await api.post<{ productId: number }>(request, "/api/v1/seller/products", token, {
    brandId,
    originalPrice,
    salePrice,
    content: {
      name,
      description: "E2E 테스트 상품입니다.",
      categoryId,
      imageFileIds,
      options,
    },
  });
  return productId;
}

export async function approveProduct(request: APIRequestContext, admin: string, productId: number) {
  await api.post(request, `/api/v1/admin/products/${productId}/approve`, admin);
}

export async function rejectChangeRequest(request: APIRequestContext, admin: string, changeRequestId: number, reason: string) {
  await api.post(request, `/api/v1/admin/change-requests/${changeRequestId}/reject`, admin, { reason });
}

export async function createCategory(request: APIRequestContext, admin: string, body: { parentId: number | null; name: string; displayOrder: number }) {
  return (await api.post<{ categoryId: number }>(request, "/api/v1/admin/categories", admin, body)).categoryId;
}

export async function deleteCategory(request: APIRequestContext, admin: string, categoryId: number) {
  await api.del(request, `/api/v1/admin/categories/${categoryId}`, admin);
}

const SAMPLE_IMAGE = join(__dirname, "../assets/sample.webp");

/**
 * 구매 E2E 의 준비 단계 한 번에: 판매자 회원 → 입점·승인 → 브랜드 승인 → 옵션 없는 상품 등록·승인 → SKU 재고 설정.
 * 정가는 판매가보다 넉넉히 크게 잡는다(가격 변경 테스트용).
 */
export async function createSellerWithProduct(
  request: APIRequestContext,
  admin: string,
  opts: { sellerName: string; productName: string; price: number; stock: number },
): Promise<{ sellerToken: string; sellerEmail: string; productId: number; skuId: number }> {
  const seller = await createUser(request, { name: opts.sellerName });
  const sid = await applySeller(request, seller.accessToken, `${opts.sellerName} 상사`);
  await approveSeller(request, admin, sid);
  const { brandId } = await createApprovedBrand(request, seller.accessToken, admin, `${opts.sellerName} 브랜드`);
  const imageFileId = await uploadImage(request, seller.accessToken, SAMPLE_IMAGE);
  const productId = await registerProduct(request, seller.accessToken, {
    brandId,
    name: opts.productName,
    imageFileIds: [imageFileId],
    categoryId: await leafCategoryId(request),
    originalPrice: opts.price * 2,
    salePrice: opts.price,
    options: [],
  });
  await approveProduct(request, admin, productId);
  const detail = await api.get<{ skus: { skuId: number }[] }>(request, `/api/v1/seller/products/${productId}`, seller.accessToken);
  const skuId = detail.skus[0]?.skuId;
  if (skuId === undefined) throw new Error("SKU 가 없습니다");
  await api.patch(request, `/api/v1/seller/products/${productId}/skus/${skuId}`, seller.accessToken, { extraPrice: 0, stock: opts.stock, onSale: true });
  return { sellerToken: seller.accessToken, sellerEmail: seller.email, productId, skuId };
}

/** 판매가를 즉시 바꾼다(판매가 ≤ 정가). */
export async function changeSalePrice(request: APIRequestContext, sellerToken: string, productId: number, salePrice: number) {
  await api.patch(request, `/api/v1/seller/products/${productId}/price`, sellerToken, { salePrice });
}

/** 주문서의 배송지 칸을 직접 입력으로 채운다. */
export async function fillShippingAddress(page: Page) {
  await page.getByLabel(/^수령인/).fill("홍길동");
  await page.getByLabel(/^연락처/).fill("010-1234-5678");
  await page.getByLabel(/^우편번호/).fill("06236");
  await page.getByLabel(/^주소\s*\*?$/).fill("서울 강남구 테헤란로 1");
  await page.getByLabel(/^상세 주소/).fill("101호");
}

/** 결제 페이지의 모의 결제창에서 "결제"를 누르고 주문 완료 화면을 기다린다. */
export async function payWithMock(page: Page) {
  const dialog = page.getByRole("dialog", { name: /결제/ });
  await dialog.getByRole("button", { name: "결제", exact: true }).click();
  await page.waitForURL((u) => /\/my\/orders\/\d+\/complete$/.test(u.pathname));
  await expect(page.getByRole("heading", { name: "결제 완료" })).toBeVisible();
}

const ADDRESS = { receiverName: "홍길동", phone: "010-1234-5678", zipCode: "06236", address1: "서울 강남구 테헤란로 1", address2: "101호", memo: null };

/** API 로 주문을 만들고 모의 결제까지 승인한다(준비 단계 전용). */
export async function placePaidOrderApi(request: APIRequestContext, buyerToken: string, items: { skuId: number; quantity: number }[]): Promise<{ orderId: number; orderNo: string }> {
  const quote = await api.post<{ totalAmount: number }>(request, "/api/v1/orders/quote", buyerToken, { items });
  const order = await api.post<{ orderId: number; orderNo: string; totalAmount: number }>(request, "/api/v1/orders", buyerToken, {
    items,
    expectedTotalAmount: quote.totalAmount,
    pointsToUse: 0,
    shippingAddress: ADDRESS,
  });
  await api.post(request, "/api/v1/payments/confirm", buyerToken, { orderNo: order.orderNo, paymentKey: `mock-${randomUUID()}`, amount: order.totalAmount });
  return { orderId: order.orderId, orderNo: order.orderNo };
}

/** 다른 사용자로 로그인한 별도 브라우저 컨텍스트에서 fn 을 실행하고 닫는다. */
export async function asUser(page: Page, email: string, password: string, fn: (p: Page) => Promise<void>) {
  const ctx = await page.context().browser()!.newContext({ viewport: { width: 1440, height: 900 }, baseURL: test.info().project.use.baseURL });
  try {
    const p = await ctx.newPage();
    await uiLogin(p, email, password);
    await p.waitForURL((u) => u.pathname !== "/login");
    await fn(p);
  } finally {
    await ctx.close();
  }
}

/** 화면 로그인. next 가 있으면 /login?next= 로 들어간다. */
export async function uiLogin(page: Page, email: string, password: string, next?: string) {
  await page.goto(next ? `/login?next=${encodeURIComponent(next)}` : "/login");
  await page.getByLabel("이메일").fill(email);
  await page.getByLabel("비밀번호").fill(password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
}

export function requireAdmin(): string | null {
  return DEMO_PASSWORD ? null : "KAIROS_DEMO_PASSWORD 가 없어 데모 관리자로 로그인할 수 없습니다(local,demo 백엔드 + 같은 비밀번호 필요).";
}
