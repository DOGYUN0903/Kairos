import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { basename, join } from "node:path";
import { expect, test, type APIRequestContext, type Locator, type Page } from "@playwright/test";

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
/**
 * 라벨(dt) 바로 다음의 값(dd). 금액은 이 값 노드에 toHaveText(글자 전체 일치)로 단언한다.
 * 부모 요소에 toContainText("27,000원") 을 쓰면 127,000원도 통과한다.
 */
export function valueOf(scope: Page | Locator, label: string): Locator {
  const exact = new RegExp(`^\\s*${label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*$`);
  return scope.locator("dt").filter({ hasText: exact }).locator("xpath=following-sibling::dd[1]");
}

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

/** ---- F3 혜택·후기 준비 단계(API 직접 호출) ---- */

const SAMPLE_IMAGE_PATH = join(__dirname, "../assets/sample.webp");

/** 한국 시간 기준 LocalDateTime 문자열(초 단위). 정책 발급 기간에 쓴다. */
export function kstLocal(offsetDays: number): string {
  const d = new Date(Date.now() + offsetDays * 86_400_000);
  const parts = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", dateStyle: "short", timeStyle: "medium" }).format(d);
  return parts.replace(" ", "T");
}

export type CouponPolicyBody = {
  name: string;
  couponType: "PRODUCT" | "CART";
  targetType: "ALL_PRODUCTS" | "SELLER" | "PRODUCT" | "NONE";
  targetProductId?: number;
  discountType: "FIXED" | "RATE";
  /** 정액이면 원, 정률이면 bp(10% = 1000). */
  discountValue: number;
  maxDiscount?: number;
  minOrderAmount?: number;
  totalQuantity?: number;
  validityDays?: number;
};

function withPeriod(body: CouponPolicyBody) {
  return { validFrom: kstLocal(-1), validUntil: kstLocal(30), ...body };
}

export async function createSellerCouponPolicy(request: APIRequestContext, sellerToken: string, body: CouponPolicyBody): Promise<number> {
  return (await api.post<{ policyId: number }>(request, "/api/v1/seller/coupons/policies", sellerToken, withPeriod(body))).policyId;
}

export async function createAdminCouponPolicy(request: APIRequestContext, adminTok: string, body: CouponPolicyBody): Promise<number> {
  return (await api.post<{ policyId: number }>(request, "/api/v1/admin/coupons/policies", adminTok, withPeriod(body))).policyId;
}

/** 관리자 정책 종료. 전 상품 대상 쿠폰이 다른 spec 의 상품 상세에 남지 않게 spec 이 끝나면 부른다. */
export async function endAdminCouponPolicy(request: APIRequestContext, adminTok: string, policyId: number) {
  await api.patch(request, `/api/v1/admin/coupons/policies/${policyId}/end`, adminTok);
}

export async function claimCoupon(request: APIRequestContext, buyerToken: string, policyId: number): Promise<number> {
  return (await api.post<{ couponId: number }>(request, `/api/v1/coupons/policies/${policyId}/claim`, buyerToken)).couponId;
}

/** 장바구니에 담고 cartItemId 를 돌려준다. */
export async function addCartItem(request: APIRequestContext, buyerToken: string, skuId: number, quantity: number): Promise<number> {
  await api.post(request, "/api/v1/cart/items", buyerToken, { skuId, quantity });
  const cart = await api.get<{ items: { cartItemId: number; skuId: number }[] }>(request, "/api/v1/cart", buyerToken);
  const item = cart.items.find((i) => i.skuId === skuId);
  if (!item) throw new Error(`장바구니에 sku ${skuId} 가 없습니다`);
  return item.cartItemId;
}

type OrderItemRef = { orderItemId: number; productId: number };
type SellerOrderRef = { sellerOrderId: number; sellerId: number; items: OrderItemRef[] };

export async function orderDetail(request: APIRequestContext, buyerToken: string, orderId: number) {
  return api.get<{ sellerOrders: SellerOrderRef[] }>(request, `/api/v1/orders/${orderId}`, buyerToken);
}

/** 판매자의 판매자 주문에서 첫 항목의 orderItemId. */
export async function firstOrderItemId(request: APIRequestContext, buyerToken: string, orderId: number): Promise<number> {
  const so = (await orderDetail(request, buyerToken, orderId)).sellerOrders[0];
  const item = so?.items[0];
  if (!item) throw new Error("주문 항목이 없습니다");
  return item.orderItemId;
}

/** 발송 → 배송 완료(판매자). 그 판매자의 주문만 처리한다. */
export async function shipAndDeliver(request: APIRequestContext, sellerToken: string, buyerToken: string, order: { orderId: number }) {
  const sid = await sellerId(request, sellerToken);
  const so = (await orderDetail(request, buyerToken, order.orderId)).sellerOrders.find((x) => x.sellerId === sid);
  if (!so) throw new Error("이 판매자의 판매자 주문이 없습니다");
  await api.patch(request, `/api/v1/seller/orders/${so.sellerOrderId}/ship`, sellerToken, { carrierCode: "04", trackingNumber: "1234567890" });
  await api.patch(request, `/api/v1/seller/orders/${so.sellerOrderId}/deliver`, sellerToken);
  return so.sellerOrderId;
}

/** 발송 → 배송 완료 → 구매 확정(구매자). */
export async function shipDeliverConfirm(request: APIRequestContext, sellerToken: string, buyerToken: string, order: { orderId: number }) {
  const sellerOrderId = await shipAndDeliver(request, sellerToken, buyerToken, order);
  await api.patch(request, `/api/v1/orders/${order.orderId}/seller-orders/${sellerOrderId}/confirm`, buyerToken);
  return sellerOrderId;
}

/** e2e/assets 의 이미지를 올리고 fileId 를 돌려준다. */
export async function uploadTestImage(request: APIRequestContext, token: string): Promise<number> {
  return uploadImage(request, token, SAMPLE_IMAGE_PATH);
}

/** 구매 확정된 항목에 후기를 쓴다. 사진 포함이면 500P, 아니면 100P. */
export async function writeReview(
  request: APIRequestContext,
  buyerToken: string,
  orderId: number,
  orderItemId: number,
  { withPhoto, rating = 5, content = "E2E 후기 본문입니다. 만족합니다." }: { withPhoto: boolean; rating?: number; content?: string },
): Promise<{ reviewId: number; rewardPoints: number }> {
  const imageFileIds = withPhoto ? [await uploadTestImage(request, buyerToken)] : [];
  return api.post(request, `/api/v1/orders/${orderId}/items/${orderItemId}/reviews`, buyerToken, { rating, content, imageFileIds });
}

export async function getPointBalance(request: APIRequestContext, buyerToken: string): Promise<number> {
  return (await api.get<{ balance: number }>(request, "/api/v1/points/me", buyerToken)).balance;
}

/** ---- F4 반품·정산 준비 단계 ---- */

/** 시연 데이터의 판매자(비밀번호는 KAIROS_DEMO_PASSWORD). 정산 시연 주문이 이 판매자에게 있다. */
export const DEMO_SELLER_EMAIL = "seller@kairos.demo";

/** 한 판매자의 두 번째 상품. createSellerWithProduct 가 만든 브랜드(`${sellerName} 브랜드`)에 옵션 없는 상품을 더하고 재고를 정한다. */
export async function addProductToSeller(
  request: APIRequestContext,
  admin: string,
  seller: { sellerToken: string },
  opts: { sellerName: string; productName: string; price: number; stock: number },
): Promise<{ productId: number; skuId: number }> {
  const brandId = await brandIdByName(request, seller.sellerToken, `${opts.sellerName} 브랜드`);
  const imageFileId = await uploadImage(request, seller.sellerToken, SAMPLE_IMAGE);
  const productId = await registerProduct(request, seller.sellerToken, {
    brandId,
    name: opts.productName,
    imageFileIds: [imageFileId],
    categoryId: await leafCategoryId(request),
    originalPrice: opts.price * 2,
    salePrice: opts.price,
    options: [],
  });
  await approveProduct(request, admin, productId);
  const detail = await api.get<{ skus: { skuId: number }[] }>(request, `/api/v1/seller/products/${productId}`, seller.sellerToken);
  const skuId = detail.skus[0]?.skuId;
  if (skuId === undefined) throw new Error("SKU 가 없습니다");
  await api.patch(request, `/api/v1/seller/products/${productId}/skus/${skuId}`, seller.sellerToken, { extraPrice: 0, stock: opts.stock, onSale: true });
  return { productId, skuId };
}

/** 반품 승인(API). 판매자 토큰이면 판매자 경로, 관리자면 관리자 경로. */
export async function approveReturnApi(request: APIRequestContext, token: string, returnId: number, as: "seller" | "admin" = "seller") {
  await api.patch(request, `/api/v1/${as}/returns/${returnId}/approve`, token);
}

/** 수거 확인(API). 환불이 실행되고 반품 응답(상태·환불액)을 돌려준다. */
export async function receiveReturnApi(request: APIRequestContext, token: string, returnId: number, as: "seller" | "admin" = "seller") {
  return api.patch<{ status: string; refundAmount: number }>(request, `/api/v1/${as}/returns/${returnId}/receive`, token);
}

/**
 * 한국 시간 기준 가장 최근에 끝난 주의 월요일(YYYY-MM-DD). 정산 시연 주문이 이 주에 있고,
 * 백엔드의 정산 시작일(KAIROS_SETTLEMENT_STARTDATE)도 이 날짜여야 한다.
 */
export function lastWeekMondayKst(nowMs = Date.now()): string {
  const today = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", dateStyle: "short" }).format(nowMs);
  const d = new Date(`${today}T00:00:00Z`);
  const sinceMonday = [6, 0, 1, 2, 3, 4, 5][d.getUTCDay()]!; // 일요일=0 → 월요일 이후 경과일
  d.setUTCDate(d.getUTCDate() - sinceMonday - 7);
  return d.toISOString().slice(0, 10);
}

/** 월요일 날짜의 그 주 일요일(YYYY-MM-DD). */
export function sundayOf(monday: string): string {
  const d = new Date(`${monday}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 6);
  return d.toISOString().slice(0, 10);
}

/**
 * 반품 환불 내역 상자(RefundBreakdown)의 줄 값. 합계 줄의 라벨에는 "예상"이 붙을 수 있어(환불 예정 금액) 앞부분만 맞춘다.
 * 부모에 toContainText 를 쓰지 않도록 값 노드(dd)를 돌려준다.
 */
export function refundValue(scope: Page | Locator, label: string): Locator {
  const starts = new RegExp(`^\\s*${label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`);
  return scope.locator("dt").filter({ hasText: starts }).locator("xpath=following-sibling::dd[1]");
}

/** 주문 상세에서 상품 이름으로 항목 줄(OrderItemRow)을 찾는다. */
export function itemRow(page: Page, productName: string): Locator {
  return page.getByTestId("order-item-row").filter({ has: page.getByRole("link", { name: productName, exact: true }) });
}
