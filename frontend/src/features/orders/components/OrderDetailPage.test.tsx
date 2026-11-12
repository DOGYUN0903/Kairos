import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderDetail } from "@/features/orders/api";
import { OrderDetailPage } from "@/features/orders/components/OrderDetailPage";
import { SellerOrderCard } from "@/features/orders/components/SellerOrderCard";

const h = vi.hoisted(() => ({ GET: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { GET: h.GET, POST: vi.fn(), DELETE: vi.fn() } }));
vi.mock("next/navigation", () => ({ notFound: vi.fn(), useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/hooks/use-toast", () => ({ toast: vi.fn() }));

const NOW = "2026-11-12T10:00:00+09:00";

type ReturnRequest = { returnId: number; status: string; reason: string; feeBearer: string; refundAmount: number; returnFee: number; requestedAt: string };
const returnRequest = (status: string): ReturnRequest => ({ returnId: 11, status, reason: "CHANGE_OF_MIND", feeBearer: "BUYER", refundAmount: 0, returnFee: 0, requestedAt: "2026-11-10T10:00:00" });

const order = (over: { returnableUntil?: string; returnRequest?: ReturnRequest | null; allItemsReturned?: boolean } = {}): OrderDetail =>
  ({
    orderId: 42,
    orderNo: "K20261104093015T6QH2K",
    status: "PAID",
    createdAt: "2026-11-08T09:00:00",
    paidAt: "2026-11-08T09:05:00",
    expiresAt: "2026-11-08T09:30:00",
    itemsAmount: 20000,
    buyerShippingFee: 0,
    productCouponDiscount: 0,
    cartCouponDiscount: 0,
    pointsUsed: 0,
    totalAmount: 20000,
    refundedAmount: 0,
    pointsRefunded: 0,
    shippingFeeRecovered: 0,
    remainingAmount: 20000,
    cartCouponId: null,
    payment: { status: "APPROVED", amount: 20000, refundedAmount: 0 },
    shippingAddress: { receiverName: "홍길동", phone: "010-0000-0000", zipCode: "12345", address1: "서울", address2: null, memo: null },
    sellerOrders: [
      {
        sellerOrderId: 41,
        sellerName: "판매자 A",
        status: "DELIVERED",
        allItemsReturned: over.allItemsReturned ?? false,
        returnableUntil: over.returnableUntil ?? "2026-11-14T10:00:00",
        shippingFee: 0,
        shippingFeePayer: "PLATFORM",
        carrierCode: null,
        trackingNumber: null,
        items: [
          {
            orderItemId: 7,
            productId: 3,
            productName: "와이드 코튼 팬츠",
            brandName: "브랜드 A",
            optionDisplayName: "화이트 / L",
            quantity: 1,
            lineAmount: 20000,
            refundAmount: 0,
            status: "ORDERED",
            thumbnailUrl: null,
            review: null,
            returnRequest: over.returnRequest ?? null,
          },
        ],
      },
    ],
  }) as unknown as OrderDetail;

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <OrderDetailPage orderId={42} />
    </QueryClientProvider>,
  );
}
const load = (o: OrderDetail) => h.GET.mockResolvedValue({ data: { data: o } });

describe("구매자 주문 상세의 반품 진입", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(NOW));
  });
  afterEach(() => vi.useRealTimers());

  it("기한 안이면_반품_요청_버튼과_가능_기한을_보인다", async () => {
    load(order());
    mount();
    expect(await screen.findByRole("button", { name: "반품 요청" })).toBeInTheDocument();
    expect(screen.getByText("11월 14일 10:00까지 반품 가능")).toBeInTheDocument();
  });

  it("기한이_지나면_버튼_없이_기간_경과를_알린다", async () => {
    load(order({ returnableUntil: "2026-11-11T10:00:00" }));
    mount();
    expect(await screen.findByText("반품 가능 기간이 지났습니다")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "반품 요청" })).not.toBeInTheDocument();
  });

  it("철회한_반품은_다시_요청할_수_있다", async () => {
    load(order({ returnRequest: returnRequest("WITHDRAWN") }));
    mount();
    expect(await screen.findByRole("button", { name: "반품 요청" })).toBeInTheDocument();
    expect(screen.getByText("11월 14일 10:00까지 반품 가능")).toBeInTheDocument();
  });

  it.each([
    ["REJECTED", "반품이 거절된 상품입니다"],
    ["CANCELED_BY_ADMIN", "반품이 중단된 상품입니다"],
  ])("%s는_다시_요청할_수_없고_안내와_반품_상세_링크를_보인다", async (status, text) => {
    load(order({ returnRequest: returnRequest(status) }));
    mount();
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "반품 요청" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "반품 상세" })).toHaveAttribute("href", "/my/returns/11");
  });

  it("모든_항목이_반품된_판매자_주문은_전체_반품_칩을_보인다", async () => {
    load(order({ allItemsReturned: true }));
    mount();
    expect(await screen.findByText("전체 반품")).toBeInTheDocument();
  });
});

describe("SellerOrderCard (판매자·관리자 화면)", () => {
  const so = order({ allItemsReturned: true }).sellerOrders[0]!;

  it("슬롯이_없으면_반품_진입을_그리지_않는다", () => {
    render(<SellerOrderCard sellerOrder={{ ...so, allItemsReturned: false }} />);
    expect(screen.queryByRole("button", { name: "반품 요청" })).not.toBeInTheDocument();
    expect(screen.queryByText(/반품 가능/)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "반품 상세" })).not.toBeInTheDocument();
  });

  it("모든_항목이_반품되었으면_전체_반품_칩을_보인다", () => {
    render(<SellerOrderCard sellerOrder={so} />);
    expect(screen.getByText("전체 반품")).toBeInTheDocument();
  });
});
