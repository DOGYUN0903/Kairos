import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import type { BrandDetail } from "@/features/seller/brands/api";
import { BrandDialog } from "./BrandDialog";

const updateAsync = vi.fn().mockResolvedValue(undefined);
vi.mock("@/features/seller/brands/api", () => ({
  useCreateBrand: () => ({ mutateAsync: vi.fn() }),
  useUpdateBrand: () => ({ mutateAsync: (a: unknown) => updateAsync(a) }),
}));
vi.mock("@/features/files/api", () => ({ useUploadImage: () => ({ mutateAsync: vi.fn() }) }));
vi.mock("@/hooks/use-toast", () => ({ toast: vi.fn() }));

const brand: BrandDetail = {
  createdAt: "2026-11-08T10:00:00",
  brandId: 5,
  sellerId: 1,
  name: "키로스",
  logoUrl: "/img/l.png",
  logoFileId: 77,
  status: "REJECTED",
  rejectReason: "x",
};

describe("BrandDialog 수정", () => {
  it("기존_로고는_그대로_재신청하면_같은_fileId로_보낸다", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BrandDialog mode="edit" brand={brand} open onOpenChange={vi.fn()} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "다시 신청" }));
    await waitFor(() => expect(updateAsync).toHaveBeenCalled());
    expect(updateAsync.mock.calls[0]?.[0]).toMatchObject({ values: { name: "키로스", logoFileId: 77 } });
  });

  it("로고를_삭제하면_null로_보낸다", async () => {
    updateAsync.mockClear();
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BrandDialog mode="edit" brand={brand} open onOpenChange={vi.fn()} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "1번째 이미지 삭제" }));
    await userEvent.click(screen.getByRole("button", { name: "다시 신청" }));
    await waitFor(() => expect(updateAsync).toHaveBeenCalled());
    expect(updateAsync.mock.calls[0]?.[0]).toMatchObject({ values: { logoFileId: null } });
  });
});
