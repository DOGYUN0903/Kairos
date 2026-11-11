import { cartesian, previewRows } from "@/features/seller/products/sku-preview";

type Axis = { name: string; values: string[] };

/** 등록 화면의 읽기 전용 SKU 미리 보기. 등록 API 는 재고를 받지 않는다(스펙 §9-2). */
export function SkuPreviewTable({ options }: { options: Axis[] }) {
  const axes = options.filter((o) => o.values.length > 0);
  const rows = previewRows(axes);
  const formula = axes.length > 0 ? `${axes.map((a) => a.values.length).join(" × ")} = ${cartesian(axes).length}개` : "1개";

  return (
    <section className="card" aria-labelledby="sku-h">
      <div className="flex items-center justify-between border-b border-border px-5 py-4">
        <h2 id="sku-h" className="text-[15px] font-bold">
          SKU (옵션 조합)
        </h2>
        <span className="text-xs text-text-2">{formula}</span>
      </div>
      <div className="max-h-[420px] overflow-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="bg-surface text-left text-xs font-medium text-text-2">
              <th scope="col" className="px-3.5 py-3">조합</th>
              <th scope="col" className="px-3.5 py-3 text-right">추가 금액</th>
              <th scope="col" className="px-3.5 py-3 text-right">재고</th>
              <th scope="col" className="px-3.5 py-3">판매 여부</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.displayName} className="border-b border-border">
                <td className="px-3.5 py-2.5">{r.displayName === "" ? "기본 (옵션 없음)" : r.displayName}</td>
                <td className="px-3.5 py-2.5 text-right tabular-nums text-text-2">0원</td>
                <td className="px-3.5 py-2.5 text-right tabular-nums text-text-2">0</td>
                <td className="px-3.5 py-2.5 text-text-2">판매</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="hint px-5 py-3">재고·추가 금액은 등록 후 상세에서 입력합니다. 재고가 0이거나 판매 중지인 SKU는 사용자 화면에 &quot;품절&quot;로 표시됩니다.</p>
    </section>
  );
}
