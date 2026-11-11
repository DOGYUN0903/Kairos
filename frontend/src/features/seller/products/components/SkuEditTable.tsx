"use client";

import { useRef, useState } from "react";
import { Switch } from "@/components/ui/switch";
import type { SellerSku } from "@/features/seller/products/api";
import { skuSchema, type SkuFormValues } from "@/features/seller/products/schemas";

const TH = "px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "px-3.5 py-2.5 align-middle";

/**
 * SKU 행 편집. 행마다 추가 금액·재고·판매 여부를 고치고 "저장"(바뀐 행만 활성)으로 바로 반영한다.
 * DISCONTINUED 행은 읽기 전용 "단종".
 */
export function SkuEditTable({
  skus,
  onSave,
  disabled = false,
}: {
  skus: SellerSku[];
  onSave: (skuId: number, values: SkuFormValues) => Promise<void>;
  disabled?: boolean;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="bg-surface">
            <th scope="col" className={TH}>옵션</th>
            <th scope="col" className={`${TH} text-right`}>추가 금액(원)</th>
            <th scope="col" className={`${TH} text-right`}>재고</th>
            <th scope="col" className={TH}>판매</th>
            <th scope="col" className={`${TH} text-right`}>
              <span className="sr-only">저장</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {skus.map((s) =>
            s.skuStatus === "DISCONTINUED" ? (
              <tr key={s.skuId} className="border-b border-border text-text-3">
                <th scope="row" className={`${TD} text-left font-normal`}>{s.displayName || "기본"}</th>
                <td className={`${TD} text-right tabular-nums`}>{s.extraPrice.toLocaleString("ko-KR")}</td>
                <td className={`${TD} text-right tabular-nums`}>{s.stock}</td>
                <td className={TD}>
                  <span className="rounded-sm border border-border px-1.5 py-0.5 text-[11px]">단종</span>
                </td>
                <td className={TD} />
              </tr>
            ) : (
              // 저장 후 서버 값이 바뀌면 key 가 바뀌어 행이 새 값으로 초기화된다.
              <EditableRow key={`${s.skuId}:${s.extraPrice}:${s.stock}:${s.onSale}`} sku={s} onSave={onSave} disabled={disabled} />
            ),
          )}
        </tbody>
      </table>
    </div>
  );
}

function EditableRow({
  sku,
  onSave,
  disabled,
}: {
  sku: SellerSku;
  onSave: (skuId: number, values: SkuFormValues) => Promise<void>;
  disabled: boolean;
}) {
  const [extraPrice, setExtraPrice] = useState(String(sku.extraPrice));
  const [stock, setStock] = useState(String(sku.stock));
  const [onSale, setOnSale] = useState(sku.onSale);
  const [errors, setErrors] = useState<{ extraPrice?: string; stock?: string }>({});
  const [saving, setSaving] = useState(false);
  const inFlight = useRef(false);

  const name = sku.displayName || "기본";
  const dirty = extraPrice.trim() !== String(sku.extraPrice) || stock.trim() !== String(sku.stock) || onSale !== sku.onSale;
  const off = disabled || saving;
  const errId = `sku-${sku.skuId}-err`;

  async function save() {
    if (inFlight.current) return;
    const parsed = skuSchema.safeParse({ extraPrice, stock, onSale });
    if (!parsed.success) {
      const next: { extraPrice?: string; stock?: string } = {};
      for (const issue of parsed.error.issues) {
        const k = issue.path[0];
        if ((k === "extraPrice" || k === "stock") && !next[k]) next[k] = issue.message;
      }
      setErrors(next);
      return;
    }
    setErrors({});
    inFlight.current = true;
    setSaving(true);
    try {
      await onSave(sku.skuId, parsed.data);
    } catch {
      // 토스트는 훅이 띄운다. 입력값은 그대로 둔다.
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  const message = errors.extraPrice ?? errors.stock;

  return (
    <tr className="border-b border-border">
      <th scope="row" className={`${TD} text-left font-medium`}>
        {name}
        {message && (
          <span id={errId} className="field-error mt-1 block font-normal">
            {message}
          </span>
        )}
      </th>
      <td className={`${TD} text-right`}>
        <input
          className="input ml-auto w-28 text-right tabular-nums"
          inputMode="numeric"
          autoComplete="off"
          aria-label={`${name} 추가 금액`}
          aria-invalid={errors.extraPrice ? true : undefined}
          aria-describedby={errors.extraPrice ? errId : undefined}
          value={extraPrice}
          disabled={off}
          onChange={(e) => setExtraPrice(e.target.value)}
        />
      </td>
      <td className={`${TD} text-right`}>
        <input
          className="input ml-auto w-24 text-right tabular-nums"
          inputMode="numeric"
          autoComplete="off"
          aria-label={`${name} 재고`}
          aria-invalid={errors.stock ? true : undefined}
          aria-describedby={errors.stock ? errId : undefined}
          value={stock}
          disabled={off}
          onChange={(e) => setStock(e.target.value)}
        />
      </td>
      <td className={TD}>
        <div className="flex items-center gap-2">
          <Switch aria-label={`${name} 판매`} checked={onSale} disabled={off} onCheckedChange={setOnSale} />
          <span className="text-xs text-text-2">{onSale ? "판매" : "중지"}</span>
        </div>
      </td>
      <td className={`${TD} text-right`}>
        <button type="button" className="btn btn-primary btn-sm" disabled={off || !dirty} onClick={() => void save()}>
          {saving ? "저장 중…" : "저장"}
        </button>
      </td>
    </tr>
  );
}
