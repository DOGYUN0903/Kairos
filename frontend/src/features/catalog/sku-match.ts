import type { components } from "@/api/schema";
import { lineTotal } from "@/lib/money";

export type Sku = components["schemas"]["PublicSku"];
export type ProductOption = components["schemas"]["ProductOption"];
/** optionId -> valueId */
export type Selection = Record<number, number>;

function selectedIds(selected: Selection): number[] {
  return Object.values(selected);
}

/** 모든 옵션 축을 골랐을 때 optionValueIds 집합이 같은 SKU. 옵션이 없는 상품은 빈 선택으로 단일 SKU. */
export function findSku(skus: Sku[], selected: Selection, options: ProductOption[] = []): Sku | null {
  const ids = selectedIds(selected);
  if (options.length > 0 && !options.every((o) => selected[o.optionId] !== undefined)) return null;
  const wanted = new Set(ids);
  return (
    skus.find(
      (s) => s.optionValueIds.length === wanted.size && s.optionValueIds.every((v) => wanted.has(v)),
    ) ?? null
  );
}

/**
 * 현재 선택(해당 축 자신의 선택은 제외)과 함께 만들 수 있는 판매 가능 SKU 가 없는 값.
 * SKU 가 아예 없거나 전부 품절이면 disabled.
 */
export function disabledValues(skus: Sku[], options: ProductOption[], selected: Selection): Set<number> {
  const disabled = new Set<number>();
  for (const option of options) {
    const others = Object.entries(selected)
      .filter(([optionId]) => Number(optionId) !== option.optionId)
      .map(([, valueId]) => valueId);
    for (const value of option.values) {
      const available = skus.some(
        (s) => !s.soldOut && s.optionValueIds.includes(value.valueId) && others.every((o) => s.optionValueIds.includes(o)),
      );
      if (!available) disabled.add(value.valueId);
    }
  }
  return disabled;
}

export function selectionTotal(salePrice: number, sku: Sku, qty: number): number {
  return lineTotal(salePrice, sku.extraPrice, qty);
}

/** 선택된 값 이름을 옵션 순서대로 "블랙 / L" 로. 아직 고르지 않은 축은 건너뛴다. */
export function selectionLabel(options: ProductOption[], selected: Selection): string {
  return options
    .map((o) => o.values.find((v) => v.valueId === selected[o.optionId])?.name)
    .filter((n): n is string => n !== undefined)
    .join(" / ");
}
