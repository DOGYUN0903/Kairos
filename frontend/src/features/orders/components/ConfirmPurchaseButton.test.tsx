import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ConfirmPurchaseButton } from "@/features/orders/components/ConfirmPurchaseButton";

const h = vi.hoisted(() => ({ PATCH: vi.fn(), toast: vi.fn() }));
vi.mock("@/api/client", async (importOriginal) => ({ ...(await importOriginal<typeof import("@/api/client")>()), api: { PATCH: h.PATCH } }));
vi.mock("@/hooks/use-toast", () => ({ toast: h.toast }));

describe("ConfirmPurchaseButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.PATCH.mockResolvedValue({ data: { data: {} } });
  });

  it("구매_확정을_연달아_눌러도_요청은_한_번만_나간다", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ConfirmPurchaseButton orderId={42} sellerOrderId={9} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "구매 확정" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.dblClick(within(dialog).getByRole("button", { name: "구매 확정" }));
    await waitFor(() => expect(h.toast).toHaveBeenCalledWith({ title: "구매가 확정되었습니다." }));
    expect(h.PATCH).toHaveBeenCalledTimes(1);
  });
});
