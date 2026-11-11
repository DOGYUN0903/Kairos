import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SkuEditTable } from "@/features/seller/products/components/SkuEditTable";
import type { SellerProductDetail } from "@/features/seller/products/api";

const skus: SellerProductDetail["skus"] = [
  { skuId: 1, optionValueIds: [11], displayName: "블랙", extraPrice: 0, stock: 5, onSale: true, skuStatus: "ACTIVE" },
  { skuId: 2, optionValueIds: [12], displayName: "화이트", extraPrice: 1000, stock: 3, onSale: true, skuStatus: "ACTIVE" },
  { skuId: 3, optionValueIds: [13], displayName: "레드", extraPrice: 0, stock: 0, onSale: false, skuStatus: "DISCONTINUED" },
];

const row = (name: string) => screen.getByRole("row", { name: new RegExp(name) });

describe("SkuEditTable", () => {
  it("값을_바꾼_행만_저장_버튼이_활성", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<SkuEditTable skus={skus} onSave={onSave} />);
    const black = row("블랙");
    const white = row("화이트");
    expect(within(black).getByRole("button", { name: "저장" })).toBeDisabled();
    const stock = within(black).getByLabelText("블랙 재고");
    await userEvent.clear(stock);
    await userEvent.type(stock, "0");
    expect(within(black).getByRole("button", { name: "저장" })).toBeEnabled();
    expect(within(white).getByRole("button", { name: "저장" })).toBeDisabled();
    await userEvent.click(within(black).getByRole("button", { name: "저장" }));
    expect(onSave).toHaveBeenCalledWith(1, { extraPrice: 0, stock: 0, onSale: true });
  });

  it("판매_스위치를_끄면_저장된다", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<SkuEditTable skus={skus} onSave={onSave} />);
    const white = row("화이트");
    await userEvent.click(within(white).getByRole("switch", { name: "화이트 판매" }));
    await userEvent.click(within(white).getByRole("button", { name: "저장" }));
    expect(onSave).toHaveBeenCalledWith(2, { extraPrice: 1000, stock: 3, onSale: false });
  });

  it("음수_재고는_저장하지_않는다", async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<SkuEditTable skus={skus} onSave={onSave} />);
    const black = row("블랙");
    const stock = within(black).getByLabelText("블랙 재고");
    await userEvent.clear(stock);
    await userEvent.type(stock, "-1");
    await userEvent.click(within(black).getByRole("button", { name: "저장" }));
    expect(onSave).not.toHaveBeenCalled();
    expect(within(black).getByText("재고는 0 이상의 정수로 입력해 주세요.")).toBeInTheDocument();
  });

  it("DISCONTINUED_행은_기본으로_숨기고_토글로_본다", async () => {
    render(<SkuEditTable skus={skus} onSave={vi.fn()} />);
    expect(screen.queryByRole("row", { name: /레드/ })).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "단종 1개 보기" }));
    expect(row("레드")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "단종 숨기기" }));
    expect(screen.queryByRole("row", { name: /레드/ })).toBeNull();
  });

  it("DISCONTINUED_행은_입력이_없다", async () => {
    render(<SkuEditTable skus={skus} onSave={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: "단종 1개 보기" }));
    const red = row("레드");
    expect(within(red).queryByRole("textbox")).toBeNull();
    expect(within(red).queryByRole("switch")).toBeNull();
    expect(within(red).getByText("단종")).toBeInTheDocument();
  });

  it("disabled면_모든_입력이_비활성", () => {
    render(<SkuEditTable skus={skus} onSave={vi.fn()} disabled />);
    for (const el of screen.getAllByRole("textbox")) expect(el).toBeDisabled();
    for (const el of screen.getAllByRole("switch")) expect(el).toBeDisabled();
    for (const el of screen.getAllByRole("button", { name: "저장" })) expect(el).toBeDisabled();
  });
});
