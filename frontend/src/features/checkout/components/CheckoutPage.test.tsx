import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { OrderQuote } from "@/features/checkout/api";
import { CheckoutPage } from "@/features/checkout/components/CheckoutPage";

const h = vi.hoisted(() => ({ GET: vi.fn(), POST: vi.fn(), toast: vi.fn(), replace: vi.fn() }));

vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { GET: h.GET, POST: h.POST } }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));
vi.mock("@/auth/useAuth", () => ({ useAuth: () => ({ status: "authenticated" }) }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: h.replace, push: vi.fn() }),
  useSearchParams: () => new URLSearchParams("skuId=1&qty=1"),
}));

type Body = { productCoupons: { skuId: number; couponId: number }[]; cartCouponId: number | null; pointsToUse: number };

const coupon = { couponId: 10, name: "신규 2,000원 쿠폰", issuerType: "PLATFORM", discountType: "FIXED", discountValue: 2000, expiresAt: "2026-12-31T23:59:59", usable: true, expectedDiscount: 2000 };

/** 선택에 따라 서버가 돌려주는 견적. 숫자는 테스트가 정한 서버 값이다. */
function quoteFor(body: Body, over: Partial<OrderQuote> = {}): OrderQuote {
  const couponOn = body.productCoupons.length > 0;
  return {
    items: [
      {
        skuId: 1, productId: 1, productName: "와이드 데님 팬츠", brandName: "브랜드", optionDisplayName: "인디고 / 32", thumbnailUrl: "", unitPrice: 30000, sellerId: 1, quantity: 1,
        lineAmount: 30000, productCouponDiscount: couponOn ? 2000 : 0, cartCouponAllocated: 0, pointsAllocated: 0, paidAmount: 30000, availableCoupons: [coupon],
      },
    ],
    sellerQuotes: [{ sellerId: 1, sellerName: "판매자 B", itemsAmount: 30000, shippingFee: 0, shippingFeePayer: "PLATFORM" }],
    freeShippingThreshold: 30000, baseShippingFee: 3000, itemsAmount: 30000,
    productCouponDiscount: couponOn ? 2000 : 0, cartCouponDiscount: 0, pointsUsed: body.pointsToUse, buyerShippingFee: 0,
    totalAmount: couponOn ? 28000 : 30000, maxUsablePoints: 9000, cartCoupons: [],
    ...over,
  } as unknown as OrderQuote;
}

const ok = (data: unknown) => ({ data: { data } });
const quoteCalls = () => h.POST.mock.calls.filter((c) => c[0] === "/api/v1/orders/quote");
const orderCalls = () => h.POST.mock.calls.filter((c) => c[0] === "/api/v1/orders");

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(qc, "invalidateQueries");
  render(
    <QueryClientProvider client={qc}>
      <CheckoutPage />
    </QueryClientProvider>,
  );
  return { invalidate };
}

async function fillAddress() {
  await userEvent.type(screen.getByLabelText(/수령인/), "김민지");
  await userEvent.type(screen.getByLabelText(/연락처/), "010-1234-5678");
  await userEvent.type(screen.getByLabelText(/우편번호/), "04524");
  await userEvent.type(screen.getByLabelText(/^주소/), "서울특별시 중구 세종대로 110");
  await userEvent.type(screen.getByLabelText(/상세 주소/), "302호");
}

async function pickProductCoupon() {
  await userEvent.click(await screen.findByRole("button", { name: /쿠폰 선택/ }));
  const dialog = await screen.findByRole("dialog");
  await userEvent.click(within(dialog).getByRole("radio", { name: coupon.name }));
  await userEvent.click(within(dialog).getByRole("button", { name: "적용하기" }));
}

describe("CheckoutPage 쿠폰·포인트", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.GET.mockImplementation(async (path: string) => (path === "/api/v1/points/me" ? ok({ balance: 20000, expiringWithin30Days: 0 }) : ok({ items: [] })));
    h.POST.mockImplementation(async (path: string, opts: { body: Body }) => (path === "/api/v1/orders/quote" ? ok(quoteFor(opts.body)) : ok({ orderId: 9, orderStatus: "PENDING_PAYMENT", totalAmount: 28000 })));
  });

  it("쿠폰을_고른_직후_새_견적이_오기_전에는_결제할_수_없고_도착하면_그_견적의_금액과_선택이_나간다", async () => {
    mount();
    expect(await screen.findByRole("button", { name: "30,000원 결제하기" })).toBeEnabled();
    await fillAddress();

    let release!: () => void;
    h.POST.mockImplementationOnce(
      (_path: string, opts: { body: Body }) => new Promise((resolve) => (release = () => resolve(ok(quoteFor(opts.body))))),
    );
    await pickProductCoupon();

    // 새 견적을 기다리는 동안: 옛 금액(30,000원)이 보이지만 버튼은 막혀 있고, Enter 제출도 주문을 보내지 않는다.
    await waitFor(() => expect(screen.getByRole("button", { name: /결제하기/ })).toBeDisabled());
    fireEvent.submit(screen.getByRole("button", { name: /결제하기/ }).closest("form")!);
    expect(orderCalls()).toHaveLength(0);

    release();
    const pay = await screen.findByRole("button", { name: "28,000원 결제하기" });
    await waitFor(() => expect(pay).toBeEnabled());
    await userEvent.click(pay);
    await waitFor(() => expect(orderCalls()).toHaveLength(1));
    expect(orderCalls()[0]![1].body).toMatchObject({ expectedTotalAmount: 28000, productCoupons: [{ skuId: 1, couponId: 10 }], cartCouponId: null, pointsToUse: 0 });
    await waitFor(() => expect(h.replace).toHaveBeenCalledWith("/my/orders/9/pay?auto=1"));
  });

  it("선택한_쿠폰이_견적에서_거절되면_직전_선택으로_되돌리고_견적_캐시를_무효화한다", async () => {
    const { invalidate } = mount();
    await screen.findByRole("button", { name: "30,000원 결제하기" });
    h.POST.mockImplementationOnce(async () => {
      throw new ApiError(409, "COUPON_NOT_AVAILABLE", "이미 사용한 쿠폰입니다", null);
    });
    await pickProductCoupon();
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith(expect.objectContaining({ title: "쿠폰을 사용할 수 없어 선택을 되돌렸습니다." })));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["checkout", "quote"] });
    // 오류 화면에 갇히지 않고 쿠폰 없는 견적으로 돌아온다.
    expect(await screen.findByRole("button", { name: "30,000원 결제하기" })).toBeEnabled();
    expect(screen.getByText("와이드 데님 팬츠")).toBeInTheDocument();
  });

  it("되돌린_선택도_계속_거절되면_멈추고_오류를_보인다(무한_재시도_없음)", async () => {
    mount();
    await screen.findByRole("button", { name: "30,000원 결제하기" });
    h.POST.mockImplementation(async (path: string) => {
      if (path === "/api/v1/orders/quote") throw new ApiError(409, "COUPON_NOT_AVAILABLE", "이미 사용한 쿠폰입니다", null);
      return ok({});
    });
    await pickProductCoupon();
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
    const calls = quoteCalls().length;
    await new Promise((r) => setTimeout(r, 300));
    expect(quoteCalls()).toHaveLength(calls);
    expect(calls).toBeLessThanOrEqual(5);
    expect(screen.getByRole("button", { name: /결제하기/ })).toBeDisabled();
  });

  it("10P_단위가_아닌_포인트는_견적을_부르지_않고_안내를_보이며_결제를_막는다", async () => {
    mount();
    await screen.findByRole("button", { name: "30,000원 결제하기" });
    const before = quoteCalls().length;
    await userEvent.type(screen.getByLabelText("사용할 포인트"), "15");
    expect(await screen.findByText("10P 단위로 입력해 주세요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /결제하기/ })).toBeDisabled();
    await new Promise((r) => setTimeout(r, 700));
    expect(quoteCalls()).toHaveLength(before);
  });

  it("올바른_포인트는_입력이_멈춘_뒤_견적에_반영되고_그_사이에는_결제할_수_없다", async () => {
    mount();
    await screen.findByRole("button", { name: "30,000원 결제하기" });
    await userEvent.type(screen.getByLabelText("사용할 포인트"), "1000");
    expect(screen.getByRole("button", { name: /결제하기/ })).toBeDisabled();
    await waitFor(() => expect(quoteCalls().some((c) => c[1].body.pointsToUse === 1000)).toBe(true), { timeout: 3000 });
    await waitFor(() => expect(screen.getByRole("button", { name: /결제하기/ })).toBeEnabled());
  });

  it("결제_금액이_0원이면_주문하기로_바뀌고_PAID_응답은_완료_화면으로_보낸다", async () => {
    h.POST.mockImplementation(async (path: string, opts: { body: Body }) =>
      path === "/api/v1/orders/quote" ? ok(quoteFor(opts.body, { totalAmount: 0, maxUsablePoints: 0 })) : ok({ orderId: 9, orderStatus: "PAID", totalAmount: 0 }),
    );
    const { invalidate } = mount();
    const btn = await screen.findByRole("button", { name: "주문하기" });
    expect(screen.getByLabelText("사용할 포인트")).toBeDisabled();
    await fillAddress();
    await waitFor(() => expect(btn).toBeEnabled());
    await userEvent.click(btn);
    await waitFor(() => expect(h.replace).toHaveBeenCalledWith("/my/orders/9/complete"));
    expect(orderCalls()[0]![1].body.expectedTotalAmount).toBe(0);
    // 완료 화면으로 바로 가므로 장바구니·견적 캐시를 여기서 다시 읽게 한다.
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["cart"] });
    // 견적은 낡은 것으로만 표시하고 다시 요청하지 않는다(아직 켜져 있는 견적 조회가 409 를 받는다).
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["checkout", "quote"], refetchType: "none" });
  });

  it("주문이_만들어진_뒤에는_견적을_다시_요청하지_않는다", async () => {
    h.POST.mockImplementation(async (path: string, opts: { body: Body }) =>
      path === "/api/v1/orders/quote" ? ok(quoteFor(opts.body, { totalAmount: 0, maxUsablePoints: 0 })) : ok({ orderId: 9, orderStatus: "PAID", totalAmount: 0 }),
    );
    mount();
    const btn = await screen.findByRole("button", { name: "주문하기" });
    await fillAddress();
    await waitFor(() => expect(btn).toBeEnabled());
    const before = quoteCalls().length;
    await userEvent.click(btn);
    await waitFor(() => expect(h.replace).toHaveBeenCalledWith("/my/orders/9/complete"));
    await new Promise((r) => setTimeout(r, 50));
    expect(quoteCalls().length).toBe(before);
  });
});

/** 쿠폰 1장과 포인트 1,000P 를 고르고 새 견적이 확정될 때까지 기다린 뒤 주소를 채운다. */
async function selectCouponAndPoints() {
  await screen.findByRole("button", { name: "30,000원 결제하기" });
  await userEvent.type(screen.getByLabelText("사용할 포인트"), "1000");
  await waitFor(() => expect(quoteCalls().some((c) => c[1].body.pointsToUse === 1000)).toBe(true), { timeout: 3000 });
  await pickProductCoupon();
  const pay = await screen.findByRole("button", { name: "28,000원 결제하기" });
  await waitFor(() => expect(pay).toBeEnabled());
  await fillAddress();
  return pay;
}
const lastQuoteBody = (): Body => quoteCalls().at(-1)![1].body;

describe("CheckoutPage 주문 생성 오류 (스펙 7-4)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.GET.mockImplementation(async (path: string) => (path === "/api/v1/points/me" ? ok({ balance: 20000, expiringWithin30Days: 0 }) : ok({ items: [] })));
    h.POST.mockImplementation(async (path: string, opts: { body: Body }) => {
      if (path === "/api/v1/orders/quote") return ok(quoteFor(opts.body));
      throw new ApiError(0, "INTERNAL_ERROR", "x", null);
    });
  });
  const failOrder = (status: number, code: ApiError["code"], message = "서버 메시지") =>
    h.POST.mockImplementation(async (path: string, opts: { body: Body }) => {
      if (path === "/api/v1/orders/quote") return ok(quoteFor(opts.body));
      throw new ApiError(status, code, message, null);
    });
  const settle = () => new Promise((r) => setTimeout(r, 300));

  it("쿠폰_코드는_쿠폰_선택만_비우고_포인트는_유지한_채_다시_견적하며_재제출하지_않는다", async () => {
    failOrder(409, "COUPON_NOT_AVAILABLE");
    const { invalidate } = mount();
    const pay = await selectCouponAndPoints();
    await userEvent.click(pay);
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith(expect.objectContaining({ title: "쿠폰을 사용할 수 없습니다. 다시 선택해 주세요." })));
    await waitFor(() => expect(lastQuoteBody()).toMatchObject({ productCoupons: [], pointsToUse: 1000 }));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["checkout", "quote"] });
    await settle();
    expect(orderCalls()).toHaveLength(1);
  });

  it("포인트_코드는_포인트를_0으로_비우고_쿠폰은_유지한_채_다시_견적하며_재제출하지_않는다", async () => {
    failOrder(409, "POINT_INSUFFICIENT", "포인트가 부족합니다");
    mount();
    const pay = await selectCouponAndPoints();
    await userEvent.click(pay);
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith(expect.objectContaining({ title: "포인트가 부족합니다" })));
    await waitFor(() => expect(lastQuoteBody()).toMatchObject({ productCoupons: [{ skuId: 1, couponId: 10 }], pointsToUse: 0 }));
    expect(screen.getByLabelText("사용할 포인트")).toHaveValue("");
    await settle();
    expect(orderCalls()).toHaveLength(1);
  });

  it("ORDER_PRICE_CHANGED는_선택을_유지한_채_다시_견적하고_재제출하지_않는다", async () => {
    failOrder(409, "ORDER_PRICE_CHANGED");
    mount();
    const pay = await selectCouponAndPoints();
    const before = quoteCalls().length;
    await userEvent.click(pay);
    expect(await screen.findByText("금액이 변경되었습니다.")).toBeInTheDocument();
    await waitFor(() => expect(quoteCalls().length).toBeGreaterThan(before));
    expect(lastQuoteBody()).toMatchObject({ productCoupons: [{ skuId: 1, couponId: 10 }], pointsToUse: 1000 });
    await settle();
    expect(orderCalls()).toHaveLength(1);
  });
});
