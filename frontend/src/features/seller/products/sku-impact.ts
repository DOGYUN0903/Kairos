import type { components } from "@/api/schema";

export type OptionResult = components["schemas"]["ProductOption"];

/** 변경 요청 옵션(유지하는 옵션·값은 ID, 새것은 null). */
export type ChangeOption = { optionId: number | null; name: string; values: { valueId: number | null; name: string }[] };

type CurrentSkus = { options: OptionResult[]; skus: { skuId: number; optionValueIds: number[]; displayName: string }[] };

const keyOf = (ids: number[]) => [...ids].sort((a, b) => a - b).join(",");

/**
 * 옵션 변경이 SKU 에 주는 영향(백엔드 반영 규칙과 같다).
 * - 유지: 다음 옵션의 값 ID(모두 비null) 조합과 같은 기존 SKU
 * - 단종: 그 외 기존 SKU
 * - 신규: 새 조합(값 ID 가 null 이거나 기존 조합에 없음)
 */
export type SkuImpact = { kept: string[]; added: string[]; discontinued: string[]; renamed: { from: string; to: string }[] };

export function skuImpact(current: CurrentSkus, next: ChangeOption[]): SkuImpact {
  const axes = next.filter((o) => o.values.length > 0);
  type Combo = { ids: (number | null)[]; names: string[] };
  const combos = axes.reduce<Combo[]>(
    (rows, axis) => rows.flatMap((r) => axis.values.map((v) => ({ ids: [...r.ids, v.valueId], names: [...r.names, v.name] }))),
    [{ ids: [], names: [] }],
  );

  const existing = new Map(current.skus.map((s) => [keyOf(s.optionValueIds), s]));
  const keptIds = new Set<number>();
  const kept: string[] = [];
  const renamed: { from: string; to: string }[] = [];
  const added: string[] = [];
  for (const c of combos) {
    const name = c.names.join(" / ");
    const ids = c.ids.every((id): id is number => id !== null) ? c.ids : null;
    const match = ids ? existing.get(keyOf(ids)) : undefined;
    if (match) {
      keptIds.add(match.skuId);
      kept.push(name);
      // 값 ID 가 같으니 SKU 는 유지되고 표시 이름만 바뀐다.
      if (match.displayName !== name) renamed.push({ from: match.displayName, to: name });
    } else {
      added.push(name);
    }
  }
  const discontinued = current.skus.filter((s) => !keptIds.has(s.skuId)).map((s) => s.displayName);
  return { kept, added, discontinued, renamed };
}
