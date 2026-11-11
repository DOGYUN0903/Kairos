"use client";

import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { Skeleton } from "@/components/ui/skeleton";
import { useCategories } from "@/features/catalog/api";
import { findCategoryPath } from "@/features/catalog/category-path";
import { useAdminProduct } from "@/features/admin/review/api";
import { DetailShell, Facts } from "@/features/admin/review/components/DetailShell";
import type { QueueRow } from "@/features/admin/review/rows";
import { formatDateTime } from "@/lib/datetime";

export function useCategoryLabel(categoryId: number): string {
  const categories = useCategories();
  const path = categories.data ? findCategoryPath(categories.data, categoryId) : [];
  return path.length > 0 ? path.map((n) => n.name).join(" > ") : `카테고리 #${categoryId}`;
}

/** id 로 직접 읽는다. `hint`(큐 행)가 있으면 판매자·등록일을 덧붙인다(없어도 동작). */
export function ProductDetailCard({ id, hint, onDone }: { id: number; hint?: QueueRow | undefined; onDone: (id: number) => void }) {
  const q = useAdminProduct(id);
  const p = q.data;
  const category = useCategoryLabel(p?.categoryId ?? 0);
  const title = `${p?.name ?? ""} 상품 검수`.trim();
  const sub = [`#${id}`, p?.brandName, hint?.sellerName, hint ? `등록 ${formatDateTime(hint.at)}` : null].filter(Boolean).join(" · ");

  if (q.isError) {
    return (
      <DetailShell row={null} title={title} sub={sub} onDone={onDone}>
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      </DetailShell>
    );
  }
  if (!p) {
    return (
      <DetailShell row={null} title={title} sub={sub} onDone={onDone}>
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3">
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      </DetailShell>
    );
  }
  const active = p.skus.filter((s) => s.skuStatus === "ACTIVE");
  return (
    <DetailShell row={{ tab: "products", id, name: p.name, status: p.status }} title={title} sub={sub} onDone={onDone}>
      <div className="flex flex-wrap gap-2" aria-label="상품 이미지">
        {p.images.map((img, i) => (
          // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
          <img key={img.fileId} src={img.url} alt={`${p.name} 이미지 ${i + 1}${img.isMain ? " (대표)" : ""}`} className="h-24 w-24 rounded border border-border object-cover" />
        ))}
      </div>
      <Facts
        items={[
          ["상품명", p.name],
          ["카테고리", category],
          ["정가", <Money key="o" value={p.originalPrice} />],
          [
            "판매가",
            <span key="s">
              <Money value={p.salePrice} className="font-bold" />
              {p.discountRate > 0 && <strong className="ml-2 text-danger">{p.discountRate}%</strong>}
            </span>,
          ],
          [
            "옵션",
            p.options.length === 0 ? "없음" : p.options.map((o) => `${o.name}: ${o.values.map((v) => v.name).join(", ")}`).join(" / "),
          ],
        ]}
      />
      <div>
        <h3 className="mb-1 text-xs font-medium text-text-2">설명</h3>
        <p className="whitespace-pre-wrap">{p.description}</p>
      </div>
      <div>
        <h3 className="mb-1 text-xs font-medium text-text-2">SKU {active.length}개</h3>
        <table className="w-full border-collapse text-[13px]">
          <thead>
            <tr className="bg-surface text-left text-xs text-text-2">
              <th scope="col" className="px-2.5 py-2 font-medium">조합</th>
              <th scope="col" className="px-2.5 py-2 text-right font-medium">추가 금액</th>
              <th scope="col" className="px-2.5 py-2 text-right font-medium">재고</th>
            </tr>
          </thead>
          <tbody>
            {active.map((s) => (
              <tr key={s.skuId} className="border-b border-border">
                <td className="px-2.5 py-2">{s.displayName || "기본"}</td>
                <td className="px-2.5 py-2 text-right"><Money value={s.extraPrice} /></td>
                <td className="px-2.5 py-2 text-right tabular-nums">{s.stock}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </DetailShell>
  );
}
