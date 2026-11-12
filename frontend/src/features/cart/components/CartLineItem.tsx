"use client";

import { Minus, Plus, X } from "lucide-react";
import Link from "next/link";
import { useEffect, useId, useState } from "react";
import { Money } from "@/components/common/Money";
import type { CartItem } from "@/features/cart/api";
import type { QuantitySync } from "@/features/cart/quantity-sync";
import { clampQuantity, isSelectable, unavailableLabel } from "@/features/cart/selection";
import { cn } from "@/lib/utils";

const ring = "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-navy focus-visible:ring-offset-1";

/** 입력은 QuantitySync 가 300ms 모아 행마다 하나씩 PATCH 한다. 낙관적 갱신은 없고, 금액은 응답 뒤 다시 읽은 값이다. */
function CartQuantity({ item, sync, dirty }: { item: CartItem; sync: QuantitySync; dirty: boolean }) {
  const id = useId();
  const [draft, setDraft] = useState(String(item.quantity));

  // 저장되지 않은 입력이 없을 때만 서버 값을 따른다.
  useEffect(() => {
    if (!dirty) setDraft(String(item.quantity));
  }, [item.quantity, dirty]);

  const schedule = (raw: string) => {
    setDraft(raw);
    sync.set(item.cartItemId, clampQuantity(raw), item.quantity);
  };

  const current = clampQuantity(draft);
  const btn = cn("grid h-9 w-9 place-items-center disabled:cursor-not-allowed disabled:text-text-3", ring);
  return (
    <div role="group" aria-label="수량" className="inline-flex rounded border border-border bg-bg">
      <button type="button" className={btn} aria-label="수량 줄이기" disabled={current <= 1} onClick={() => schedule(String(current - 1))}>
        <Minus className="h-4 w-4" aria-hidden />
      </button>
      <label htmlFor={id} className="sr-only">
        {item.productName} 수량
      </label>
      <input
        id={id}
        inputMode="numeric"
        value={draft}
        onChange={(e) => {
          if (/^\d{0,3}$/.test(e.target.value)) schedule(e.target.value);
        }}
        className={cn("w-10 border-0 bg-transparent text-center", ring)}
      />
      <button type="button" className={btn} aria-label="수량 늘리기" disabled={current >= 999} onClick={() => schedule(String(current + 1))}>
        <Plus className="h-4 w-4" aria-hidden />
      </button>
    </div>
  );
}

export function CartLineItem({
  item,
  checked,
  onToggle,
  onRemove,
  sync,
  dirty,
  removing,
}: {
  item: CartItem;
  checked: boolean;
  onToggle: () => void;
  onRemove: () => void;
  sync: QuantitySync;
  dirty: boolean;
  removing: boolean;
}) {
  const selectable = isSelectable(item);
  const href = `/products/${item.productId}`;
  const dim = selectable ? "" : "opacity-45";
  return (
    <div
      className={cn(
        "grid grid-cols-[20px_88px_minmax(0,1fr)_114px_110px_32px] items-center gap-4 p-5 [&+&]:border-t [&+&]:border-border",
        !selectable && "bg-[#FAFBFC]",
      )}
    >
      <input
        type="checkbox"
        checked={checked}
        disabled={!selectable}
        onChange={onToggle}
        aria-label={selectable ? `${item.productName} 선택` : `${item.productName} — ${unavailableLabel(item.availability)}되어 선택할 수 없습니다`}
        className={cn("h-[18px] w-[18px] accent-navy", ring)}
      />
      <Link href={href} tabIndex={-1} aria-hidden className={cn("block h-[88px] w-[88px] overflow-hidden rounded bg-surface-2", dim)}>
        {item.thumbnailUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(next/image 미사용)
          <img src={item.thumbnailUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="grid h-full w-full place-items-center text-[10px] text-text-3">이미지 없음</div>
        )}
      </Link>
      <div className={cn("min-w-0", dim)}>
        <div className="text-xs font-bold">{item.brandName}</div>
        <Link href={href} className="mt-0.5 block truncate text-sm font-medium hover:underline">
          {item.productName}
        </Link>
        <div className="mt-1 text-xs text-text-2">{item.optionDisplayName}</div>
        <div className="mt-0.5 text-xs tabular-nums text-text-2">
          <Money value={item.unitPrice} />
          {!selectable && ` · ${item.quantity}개`}
        </div>
      </div>
      {selectable ? (
        <CartQuantity item={item} sync={sync} dirty={dirty} />
      ) : (
        <span className="inline-flex h-[26px] items-center justify-self-center whitespace-nowrap rounded border border-border bg-bg px-2.5 text-xs font-bold text-text-2">
          {unavailableLabel(item.availability)}
        </span>
      )}
      <Money value={item.lineAmount} className={cn("text-right text-[15px] font-bold", dim)} />
      <button
        type="button"
        onClick={onRemove}
        disabled={removing}
        aria-label={`${item.productName} 삭제`}
        className={cn("grid h-8 w-8 place-items-center rounded text-text-3 hover:bg-surface hover:text-navy disabled:cursor-not-allowed disabled:opacity-50", ring)}
      >
        <X className="h-4 w-4" aria-hidden />
      </button>
    </div>
  );
}
