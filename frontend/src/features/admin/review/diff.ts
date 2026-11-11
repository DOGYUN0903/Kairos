import type { components } from "@/api/schema";
import type { SellerProductDetail } from "@/features/seller/products/api";
import { skuImpact, type ChangeOption, type SkuImpact } from "@/features/seller/products/sku-impact";

export type ChangeRequestDetail = components["schemas"]["ChangeRequestDetail"];

export type FieldDiff = {
  field: "name" | "description" | "categoryId" | "images" | "options" | "originalPrice";
  before: unknown;
  after: unknown;
  changed: boolean;
};

type Axis = { optionId?: number | null; name: string; values: { valueId?: number | null; name: string }[] };

const optionsKey = (options: Axis[]) =>
  JSON.stringify(options.map((o) => [o.optionId ?? null, o.name, o.values.map((v) => [v.valueId ?? null, v.name])]));

const toChangeOptions = (options: Axis[]): ChangeOption[] =>
  options.map((o) => ({ optionId: o.optionId ?? null, name: o.name, values: o.values.map((v) => ({ valueId: v.valueId ?? null, name: v.name })) }));

/**
 * 현재 상품과 변경 요청의 전/후. 이미지는 fileId 배열(순서 포함)로 비교하고,
 * 현재 상품에 없던 fileId 는 newImageIds 로 돌려준다. 옵션 변경의 SKU 영향은 impact.
 */
export function contentDiff(
  current: SellerProductDetail,
  request: ChangeRequestDetail,
): { diffs: FieldDiff[]; newImageIds: Set<number>; impact: SkuImpact } {
  const c = request.content;
  const beforeImages = current.images.map((i) => i.fileId);
  const afterImages = c.images.map((i) => i.fileId);
  const same = <T>(a: T, b: T) => a === b;
  const diffs: FieldDiff[] = [
    { field: "name", before: current.name, after: c.name, changed: !same(current.name, c.name) },
    { field: "description", before: current.description, after: c.description, changed: !same(current.description, c.description) },
    { field: "categoryId", before: current.categoryId, after: c.categoryId, changed: !same(current.categoryId, c.categoryId) },
    { field: "images", before: beforeImages, after: afterImages, changed: beforeImages.join(",") !== afterImages.join(",") },
    { field: "options", before: current.options, after: c.options, changed: optionsKey(current.options) !== optionsKey(c.options) },
    { field: "originalPrice", before: current.originalPrice, after: request.originalPrice, changed: !same(current.originalPrice, request.originalPrice) },
  ];
  const known = new Set(beforeImages);
  const newImageIds = new Set(afterImages.filter((id) => !known.has(id)));
  const impact = skuImpact(
    { options: current.options, skus: current.skus.filter((s) => s.skuStatus === "ACTIVE") },
    toChangeOptions(c.options),
  );
  return { diffs, newImageIds, impact };
}
