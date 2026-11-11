import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { SellerProductDetail } from "@/features/seller/products/api";
import { SkuImpactNotice } from "@/features/seller/products/components/SkuImpactNotice";

const sku = (skuId: number, ids: number[], displayName: string, skuStatus: "ACTIVE" | "DISCONTINUED" = "ACTIVE") => ({
  skuId, optionValueIds: ids, displayName, extraPrice: 0, stock: 1, onSale: true, skuStatus,
});
const initial = {
  options: [{ optionId: 1, name: "색상", values: [{ valueId: 11, name: "블랙" }, { valueId: 12, name: "화이트" }] }],
  skus: [sku(101, [11], "블랙"), sku(102, [12], "화이트"), sku(103, [13], "레드", "DISCONTINUED")],
} as unknown as SellerProductDetail;

describe("SkuImpactNotice", () => {
  it("옵션이_그대로면_모두_유지", () => {
    render(<SkuImpactNotice initial={initial} options={[{ optionId: 1, name: "색상", values: ["블랙", "화이트"], valueIds: [11, 12] }]} />);
    expect(screen.getByText("유지 2 / 신규 0(재고 0) / 단종 0")).toBeInTheDocument();
  });

  it("이름을_바꾸면_단종_신규가_아니라_이름_변경으로_보인다", () => {
    render(<SkuImpactNotice initial={initial} options={[{ optionId: 1, name: "컬러", values: ["검정", "화이트"], valueIds: [11, 12] }]} />);
    expect(screen.getByText("유지 2 / 신규 0(재고 0) / 단종 0")).toBeInTheDocument();
    expect(screen.getByText("이름 변경 1")).toBeInTheDocument();
    expect(screen.getByText("블랙 → 검정")).toBeInTheDocument();
  });

  it("값을_빼고_추가하면_신규와_단종을_보여_준다_이미_단종된_SKU는_세지_않는다", () => {
    render(<SkuImpactNotice initial={initial} options={[{ optionId: 1, name: "색상", values: ["블랙", "네이비"], valueIds: [11, null] }]} />);
    expect(screen.getByText("유지 1 / 신규 1(재고 0) / 단종 1")).toBeInTheDocument();
    expect(screen.getByText("네이비")).toBeInTheDocument();
    expect(screen.getByText("화이트")).toBeInTheDocument();
  });
});
