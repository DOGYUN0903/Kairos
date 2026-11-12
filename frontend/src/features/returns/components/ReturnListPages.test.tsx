import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { AdminReturnListPage } from "@/features/admin/returns/components/AdminReturnListPage";
import { MyReturnsPage } from "@/features/returns/components/MyReturnsPage";
import { SellerReturnListPage } from "@/features/seller/returns/components/SellerReturnListPage";
import { queryKeys } from "@/lib/query-keys";

const h = vi.hoisted(() => ({ GET: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { GET: h.GET } }));
vi.mock("next/navigation", () => ({
  notFound: vi.fn(),
  usePathname: () => "/x",
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

const row = {
  returnId: 5,
  orderId: 40,
  sellerOrderId: 41,
  orderItemId: 7,
  productName: "램스울 크루넥 니트",
  quantity: 1,
  reason: "CHANGE_OF_MIND",
  feeBearer: "BUYER",
  status: "REQUESTED",
  returnFee: 0,
  outboundFeeRecovered: 0,
  shippingFeeRefund: 0,
  sellerBorneFee: 0,
  refundAmount: 0,
  pointsRefund: 0,
  requestedAt: "2026-11-09T11:02:00",
  thumbnailUrl: null,
  optionDisplayName: "오트밀 / M",
  orderNo: "K20261104093015T6QH2K",
  sellerName: "판매자 A",
  paidAmount: 30000,
  autoApproveAt: "2026-11-12T11:02:00",
};
const page = { content: [row], page: 0, size: 10, totalElements: 1, totalPages: 1 };

const cases = [
  { name: "내 반품", ui: <MyReturnsPage />, key: queryKeys.returns.mine({ page: 0, size: 10 }) },
  { name: "판매자 반품", ui: <SellerReturnListPage />, key: queryKeys.sellerReturns.list({ page: 0, size: 20 }) },
  { name: "관리자 반품", ui: <AdminReturnListPage />, key: queryKeys.adminReturns.list({ page: 0, size: 20 }) },
];

describe.each(cases)("$name 목록", ({ ui, key }) => {
  beforeEach(() => vi.clearAllMocks());

  it("같은_조건의_행이_있으면_다시_읽기가_실패해도_행을_유지하고_오류와_다시_시도를_보인다", async () => {
    h.GET.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(key, page);
    render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
    await waitFor(() => expect(screen.getByRole("button", { name: /다시 시도/ })).toBeInTheDocument());
    expect(screen.getByText("램스울 크루넥 니트")).toBeInTheDocument();
  });

  it("행이_없을_때_실패하면_오류만_보인다", async () => {
    h.GET.mockRejectedValue(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null));
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>);
    expect(await screen.findByRole("button", { name: /다시 시도/ })).toBeInTheDocument();
    expect(screen.queryByText("램스울 크루넥 니트")).not.toBeInTheDocument();
  });
});
