"use client";

import { useMemo } from "react";
import type { SellerProductDetail } from "@/features/seller/products/api";
import { toChangeOptions } from "@/features/seller/products/schemas";
import { skuImpact } from "@/features/seller/products/sku-impact";

type Axis = { name: string; values: string[] };

/** 옵션 변경이 SKU 에 주는 영향 미리 보기: 유지 n / 신규 n(재고 0) / 단종 n + 목록. */
export function SkuImpactNotice({ initial, options }: { initial: SellerProductDetail; options: Axis[] }) {
  const impact = useMemo(() => {
    const active = initial.skus.filter((s) => s.skuStatus === "ACTIVE");
    return skuImpact({ options: initial.options, skus: active }, toChangeOptions(options, initial.options));
  }, [initial, options]);
  const changed = impact.added.length > 0 || impact.discontinued.length > 0;

  return (
    <section className="card" aria-labelledby="impact-h">
      <div className="flex items-center justify-between border-b border-border px-5 py-4">
        <h2 id="impact-h" className="text-[15px] font-bold">
          SKU 영향
        </h2>
        <span className="text-xs text-text-2" aria-live="polite">
          유지 {impact.kept.length} / 신규 {impact.added.length}(재고 0) / 단종 {impact.discontinued.length}
        </span>
      </div>
      <div className="flex flex-col gap-3 p-5 text-sm">
        {!changed ? (
          <p className="text-text-2">옵션 구성이 그대로라 SKU는 모두 유지됩니다. 재고·추가 금액은 그대로입니다.</p>
        ) : (
          <div className={impact.discontinued.length > 0 ? "notice notice-warn" : "notice"} role="status">
            <div>
              승인되면 SKU가 아래처럼 바뀝니다. 신규 SKU는 재고 0으로 시작하고, 단종된 SKU는 다시 판매할 수 없습니다. 옵션명이나 값 이름을 바꾸면 새 옵션으로
              취급됩니다.
            </div>
          </div>
        )}
        <ImpactList title="유지" items={impact.kept} />
        <ImpactList title="신규 (재고 0)" items={impact.added} />
        <ImpactList title="단종" items={impact.discontinued} danger />
      </div>
    </section>
  );
}

function ImpactList({ title, items, danger = false }: { title: string; items: string[]; danger?: boolean }) {
  if (items.length === 0) return null;
  return (
    <div>
      <h3 className={`mb-1 text-xs font-medium ${danger ? "text-danger" : "text-text-2"}`}>
        {title} {items.length}
      </h3>
      <ul className="flex flex-wrap gap-1.5">
        {items.map((n, i) => (
          <li key={`${n}-${i}`} className="rounded-sm border border-border px-1.5 py-0.5 text-[12px]">
            {n || "기본"}
          </li>
        ))}
      </ul>
    </div>
  );
}
