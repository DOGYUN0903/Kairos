import type { SellerChangeRequestDetail, SellerProductDetail } from "@/features/seller/products/api";

export type ChangedField = "상품명" | "설명" | "카테고리" | "이미지" | "옵션" | "정가";

const optionsKey = (options: { optionId?: number | null; name: string; values: { valueId?: number | null; name: string }[] }[]) =>
  JSON.stringify(options.map((o) => [o.optionId ?? null, o.name, o.values.map((v) => [v.valueId ?? null, v.name])]));

/** 변경 요청이 현재 상품과 다른 항목. 판매자에게 "무엇을 요청했는지" 요약으로 보여 준다. */
export function changedFields(product: SellerProductDetail, request: SellerChangeRequestDetail): ChangedField[] {
  const c = request.content;
  const out: ChangedField[] = [];
  if (c.name !== product.name) out.push("상품명");
  if (c.description !== product.description) out.push("설명");
  if (c.categoryId !== product.categoryId) out.push("카테고리");
  if (c.images.map((i) => i.fileId).join(",") !== product.images.map((i) => i.fileId).join(",")) out.push("이미지");
  if (optionsKey(c.options) !== optionsKey(product.options)) out.push("옵션");
  if (request.originalPrice !== product.originalPrice) out.push("정가");
  return out;
}
