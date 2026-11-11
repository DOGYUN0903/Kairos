"use client";

import type { ReactNode } from "react";
import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { Skeleton } from "@/components/ui/skeleton";
import { useAdminChangeRequest, useAdminProduct } from "@/features/admin/review/api";
import { useCategoryLabel } from "@/features/admin/review/components/ProductDetailCard";
import { DetailShell } from "@/features/admin/review/components/DetailShell";
import { contentDiff, type FieldDiff } from "@/features/admin/review/diff";
import type { QueueRow } from "@/features/admin/review/rows";
import { StatusChip } from "@/components/common/StatusChip";
import { discountRate } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";
import { cn } from "@/lib/utils";

type Axis = { name: string; values: { name: string }[] };

function Heading({ children, changed }: { children: ReactNode; changed: boolean }) {
  return (
    <h3 className="mb-1.5 mt-1 flex items-center gap-2 text-[13px] font-bold">
      {children}
      <span className={cn("rounded-sm px-1.5 py-px text-[11px] font-bold", changed ? "bg-[#FFF6DD] text-[#7A5600]" : "border border-border font-normal text-text-3")}>
        {changed ? "변경됨" : "변경 없음"}
      </span>
    </h3>
  );
}

function Pair({ beforeLabel = "변경 전", afterLabel = "변경 후", before, after, changed }: { beforeLabel?: string; afterLabel?: string; before: ReactNode; after: ReactNode; changed: boolean }) {
  return (
    <div className="grid grid-cols-2 gap-px overflow-hidden rounded border border-border bg-border">
      <div className="bg-surface px-3.5 py-2 text-xs font-medium text-text-2">{beforeLabel}</div>
      <div className="bg-surface px-3.5 py-2 text-xs font-medium text-text-2">{afterLabel}</div>
      <div className="bg-bg px-3.5 py-3 text-[13px]">{before}</div>
      <div className={cn("px-3.5 py-3 text-[13px]", changed ? "bg-[#FFF6DD]" : "bg-bg")}>{after}</div>
    </div>
  );
}

const optionText = (options: Axis[]) =>
  options.length === 0 ? "없음" : options.map((o) => (
    <div key={o.name}>
      <span className="block text-[11px] text-text-3">{o.name}</span>
      {o.values.map((v) => v.name).join(", ")}
    </div>
  ));

const find = (diffs: FieldDiff[], field: FieldDiff["field"]) => diffs.find((d) => d.field === field) as FieldDiff;

/** id 로 직접 읽는다. `hint`(큐 행)가 있으면 판매자명을 덧붙인다(없어도 동작). */
export function ChangeRequestDiff({ id, hint, onDone }: { id: number; hint?: QueueRow | undefined; onDone: (id: number) => void }) {
  const cr = useAdminChangeRequest(id);
  const product = useAdminProduct(cr.data?.productId ?? null);
  const categoryBefore = useCategoryLabel(product.data?.categoryId ?? 0);
  const categoryAfter = useCategoryLabel(cr.data?.content.categoryId ?? 0);
  const title = `#CR-${id} 변경 요청`;
  const sub = (
    <>
      {[cr.data ? `상품 #${cr.data.productId}` : null, product.data?.brandName, hint?.sellerName, cr.data ? `요청 ${formatDateTime(cr.data.requestedAt)}` : null].filter(Boolean).join(" · ")}
      {product.data && (
        <>
          {" "}· 현재 상태 <StatusChip kind="product" status={product.data.status} />
        </>
      )}
    </>
  );

  const error = cr.error ?? product.error;
  if (error) {
    return (
      <DetailShell row={null} title={title} sub={sub} onDone={onDone}>
        <ErrorState error={error} onRetry={() => { void cr.refetch(); void product.refetch(); }} />
      </DetailShell>
    );
  }
  if (!cr.data || !product.data) {
    return (
      <DetailShell row={null} title={title} sub={sub} onDone={onDone}>
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      </DetailShell>
    );
  }

  const current = product.data;
  const request = cr.data;
  const { diffs, newImageIds, impact } = contentDiff(current, request);
  const d = (f: FieldDiff["field"]) => find(diffs, f);
  const anyChange = diffs.some((x) => x.changed);

  // 재고·추가 금액은 현재 SKU 에서 이름으로 찾는다(신규는 재고 0).
  const skuByName = new Map(current.skus.filter((s) => s.skuStatus === "ACTIVE").map((s) => [s.displayName, s]));
  const impactRows = [
    ...impact.kept.map((n) => ({ name: n, kind: "kept" as const })),
    ...impact.added.map((n) => ({ name: n, kind: "new" as const })),
    ...impact.discontinued.map((n) => ({ name: n, kind: "disc" as const })),
  ];
  const renamedTo = new Map(impact.renamed.map((r) => [r.to, r.from]));

  return (
    <DetailShell row={{ tab: "change-requests", id, name: request.content.name, status: request.status }} title={title} sub={sub} onDone={onDone}>
      <p className="text-xs text-text-2">
        검수 중에도 기존 내용으로 계속 판매됩니다. 승인하면 아래 &quot;변경 후&quot; 내용으로 교체되고, 반려하면 변경 요청만 버려집니다.
      </p>
      {!anyChange && <p className="notice">현재 상품과 다른 항목이 없습니다.</p>}

      <div>
        <Heading changed={d("name").changed}>상품명</Heading>
        <Pair before={current.name} after={request.content.name} changed={d("name").changed} />
      </div>

      <div>
        <Heading changed={d("originalPrice").changed}>정가 · 할인율</Heading>
        <Pair
          before={<PriceCell original={current.originalPrice} sale={current.salePrice} />}
          after={<PriceCell original={request.originalPrice} sale={current.salePrice} />}
          changed={d("originalPrice").changed}
        />
        <p className="hint mt-1.5">판매가는 바로 반영되는 항목이라 변경 요청에 포함되지 않습니다. 할인율은 새 정가 기준으로 다시 계산됩니다.</p>
      </div>

      <div>
        <Heading changed={d("description").changed}>설명</Heading>
        {d("description").changed ? (
          <Pair before={<p className="whitespace-pre-wrap">{current.description}</p>} after={<p className="whitespace-pre-wrap">{request.content.description}</p>} changed />
        ) : null}
      </div>

      <div>
        <Heading changed={d("categoryId").changed}>카테고리</Heading>
        {d("categoryId").changed ? <Pair before={categoryBefore} after={categoryAfter} changed /> : null}
      </div>

      <div>
        <Heading changed={d("images").changed}>이미지</Heading>
        <Pair
          beforeLabel={`변경 전 (${current.images.length}장)`}
          afterLabel={`변경 후 (${request.content.images.length}장)`}
          changed={d("images").changed}
          before={<ImageRow images={current.images} newIds={new Set()} />}
          after={<ImageRow images={request.content.images} newIds={newImageIds} />}
        />
      </div>

      <div>
        <Heading changed={d("options").changed}>옵션 구성</Heading>
        <Pair before={optionText(current.options)} after={optionText(request.content.options)} changed={d("options").changed} />
      </div>

      {d("options").changed && (
        <div>
          <h3 className="mb-1.5 text-[13px] font-bold">
            SKU 영향{" "}
            <span className="text-xs font-normal text-text-2">
              유지 {impact.kept.length} · 신규 {impact.added.length} · 단종 {impact.discontinued.length}
            </span>
          </h3>
          <table className="w-full border-collapse text-[13px]">
            <thead>
              <tr className="bg-surface text-left text-xs text-text-2">
                <th scope="col" className="px-2.5 py-2 font-medium">조합</th>
                <th scope="col" className="px-2.5 py-2 font-medium">결과</th>
                <th scope="col" className="px-2.5 py-2 text-right font-medium">재고</th>
                <th scope="col" className="px-2.5 py-2 text-right font-medium">추가 금액</th>
              </tr>
            </thead>
            <tbody>
              {impactRows.map((r) => {
                const sku = skuByName.get(r.kind === "kept" ? (renamedTo.get(r.name) ?? r.name) : r.name);
                return (
                  <tr key={`${r.kind}-${r.name}`} className="border-b border-border">
                    <td className="px-2.5 py-2">{r.name || "기본"}</td>
                    <td className={cn("px-2.5 py-2", r.kind === "kept" && "text-text-2", r.kind === "new" && "font-bold text-warning", r.kind === "disc" && "font-bold text-danger")}>
                      {r.kind === "kept" ? "유지" : r.kind === "new" ? "신규" : "단종"}
                    </td>
                    <td className="px-2.5 py-2 text-right tabular-nums">{sku ? sku.stock : 0}</td>
                    <td className="px-2.5 py-2 text-right">{sku ? <Money value={sku.extraPrice} /> : <Money value={0} />}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <p className="hint mt-1.5">
            단종 SKU는 삭제하지 않고 DISCONTINUED로 남깁니다(주문·정산 참조 보존). 신규 SKU는 재고 0으로 시작합니다.
          </p>
        </div>
      )}
    </DetailShell>
  );
}

function PriceCell({ original, sale }: { original: number; sale: number }) {
  const rate = original > 0 && sale <= original ? discountRate(original, sale) : null;
  return (
    <>
      <span className="block text-[11px] text-text-3">정가</span>
      <Money value={original} />
      <span className="mt-2 block text-[11px] text-text-3">판매가 <Money value={sale} /> 기준 할인율</span>
      {rate === null ? <span className="text-danger">판매가가 정가보다 큽니다</span> : <strong className="text-discount">{rate}%</strong>}
    </>
  );
}

function ImageRow({ images, newIds }: { images: { fileId: number; url: string }[]; newIds: Set<number> }) {
  return (
    <div className="flex flex-wrap gap-2">
      {images.map((img, i) => (
        <div key={img.fileId} className="relative">
          {/* eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용) */}
          <img src={img.url} alt={`이미지 ${i + 1}${i === 0 ? " (대표)" : ""}${newIds.has(img.fileId) ? " (신규)" : ""}`} className={cn("h-16 w-16 rounded border object-cover", newIds.has(img.fileId) ? "border-2 border-warning" : "border-border")} />
          {newIds.has(img.fileId) && <span className="absolute -right-1 -top-1 rounded-sm bg-warning px-1 text-[10px] font-bold text-white">신규</span>}
        </div>
      ))}
    </div>
  );
}
