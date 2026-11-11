import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { OptionSelector } from "./OptionSelector";
import type { ProductOption, Sku } from "@/features/catalog/sku-match";

const options: ProductOption[] = [
  { optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }] },
  { optionId: 2, name: "사이즈", values: [{ valueId: 21, name: "M" }, { valueId: 22, name: "L" }] },
];
const sku = (skuId: number, ids: number[], soldOut = false): Sku => ({ skuId, optionValueIds: ids, displayName: "", extraPrice: 0, soldOut });
// 블랙/L 품절
const skus = [sku(1, [11, 21]), sku(2, [11, 22], true), sku(3, [12, 21]), sku(4, [12, 22])];

function setup(selected: Record<number, number> = {}, soldOut = false) {
  const onSelect = vi.fn();
  render(<OptionSelector options={options} skus={skus} selected={selected} soldOut={soldOut} onSelect={onSelect} />);
  return onSelect;
}

describe("OptionSelector", () => {
  it("축마다_그룹과_버튼을_보여주고_선택한_값은_aria-pressed다", () => {
    setup({ 1: 11 });
    expect(screen.getByRole("group", { name: /색상/ })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: /사이즈/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "블랙" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "화이트" })).toHaveAttribute("aria-pressed", "false");
  });

  it("남은_조합이_전부_품절인_값은_disabled이고_품절_이름이_붙는다", () => {
    setup({ 1: 11 });
    const l = screen.getByRole("button", { name: "L 품절" });
    expect(l).toBeDisabled();
    expect(screen.getByRole("button", { name: "M" })).toBeEnabled();
  });

  it("disabled인_값을_눌러도_선택되지_않는다", async () => {
    const onSelect = setup({ 1: 11 });
    await userEvent.click(screen.getByRole("button", { name: "L 품절" }));
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("키보드_Enter와_Space로_값을_고른다", async () => {
    const onSelect = setup();
    await userEvent.tab();
    await userEvent.keyboard("{Enter}");
    expect(onSelect).toHaveBeenLastCalledWith(1, 11);
    await userEvent.tab();
    await userEvent.keyboard(" ");
    expect(onSelect).toHaveBeenLastCalledWith(1, 12);
  });

  it("선택된_값을_다시_누르면_선택이_풀린다", async () => {
    const onSelect = setup({ 2: 21 });
    await userEvent.click(screen.getByRole("button", { name: "M" }));
    expect(onSelect).toHaveBeenCalledWith(2, null);
  });

  it("상품이_통째로_품절이면_모든_값이_disabled", () => {
    setup({}, true);
    for (const b of screen.getAllByRole("button")) expect(b).toBeDisabled();
  });
});
