"use client";

import { useEffect, useMemo, useState } from "react";
import { ApiError } from "@/api/client";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useCart, useRemoveCartItem, type CartItem } from "@/features/cart/api";
import { CartLineItem } from "@/features/cart/components/CartLineItem";
import { CartSummary } from "@/features/cart/components/CartSummary";
import {
  groupBySeller,
  initialSelection,
  isSelectable,
  pruneSelection,
  selectedQuoteItems,
  toggleAll,
  toggleItem,
  toggleSeller,
} from "@/features/cart/selection";
import { isQuoteStale, useQuantitySync } from "@/features/cart/quantity-sync";
import { useQuote } from "@/features/checkout/api";
import { queryKeys } from "@/lib/query-keys";
import { useQueryClient } from "@tanstack/react-query";

const check = "h-[18px] w-[18px] accent-navy focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-navy focus-visible:ring-offset-1";

function Heading({ count }: { count?: number }) {
  return (
    <div className="mb-6 mt-9 flex items-end justify-between gap-4">
      <h1 className="text-[26px] font-bold tracking-tight">
        장바구니
        {count !== undefined && <span className="ml-1.5 text-base font-medium text-text-2">{count}</span>}
      </h1>
      <ol aria-label="구매 단계" className="flex items-center gap-2.5 text-[13px] text-text-3">
        <li aria-current="step" className="font-bold text-navy">
          장바구니
        </li>
        <li aria-hidden>›</li>
        <li>주문서</li>
        <li aria-hidden>›</li>
        <li>주문 완료</li>
      </ol>
    </div>
  );
}

function CartBody({ items }: { items: CartItem[] }) {
  const qc = useQueryClient();
  const remove = useRemoveCartItem();
  const [rawSel, setSel] = useState<Set<number>>(() => initialSelection(items));
  const [deleting, setDeleting] = useState(false);
  const [removing, setRemoving] = useState<ReadonlySet<number>>(() => new Set());
  const { sync, dirty } = useQuantitySync();

  // 화면에서 쓰는 선택은 렌더 중에 걸러서 쓴다(다시 읽은 직후 한 번도 구매 불가 SKU 로 견적을 부르지 않는다).
  const sel = useMemo(() => pruneSelection(items, rawSel), [items, rawSel]);

  const quoteItems = useMemo(() => selectedQuoteItems(items, sel), [items, sel]);
  const quote = useQuote(quoteItems, quoteItems.length > 0);

  // 견적 사이에 품절·중지·판매자 정지가 되면 장바구니를 다시 읽어 구매 불가 표시를 갱신한다.
  useEffect(() => {
    const e = quote.error;
    if (e instanceof ApiError && e.code === "SKU_NOT_ORDERABLE") void qc.invalidateQueries({ queryKey: queryKeys.cart });
  }, [quote.error, qc]);

  const removeOne = async (id: number) => {
    if (removing.has(id)) return;
    setRemoving((s) => new Set(s).add(id));
    try {
      await remove.mutateAsync(id);
    } catch {
      // 토스트는 훅이 이미 띄웠다.
    } finally {
      setRemoving((s) => {
        const n = new Set(s);
        n.delete(id);
        return n;
      });
    }
  };

  const groups = groupBySeller(items);
  const selectable = items.filter(isSelectable);
  const selectedIds = items.filter((i) => sel.has(i.cartItemId)).map((i) => i.cartItemId);
  const allOn = selectable.length > 0 && selectable.every((i) => sel.has(i.cartItemId));

  const removeSelected = async () => {
    if (deleting || removing.size > 0) return;
    setDeleting(true);
    try {
      for (const id of selectedIds) await removeOne(id);
    } finally {
      setDeleting(false);
    }
  };

  const stale = isQuoteStale({ fetching: quote.isFetching, placeholder: quote.isPlaceholderData, unsavedCount: dirty.size });

  return (
    <div className="grid grid-cols-[minmax(0,1fr)_360px] items-start gap-10">
      <div>
        <div className="flex items-center justify-between border-b-2 border-navy pb-3 pt-1.5">
          <label className="flex min-h-9 cursor-pointer items-center gap-2 font-medium">
            <input type="checkbox" className={check} checked={allOn} disabled={selectable.length === 0} onChange={() => setSel(toggleAll(items, sel))} />
            전체 선택{" "}
            <span className="text-text-2">
              ({selectedIds.length}/{selectable.length})
            </span>
          </label>
          <button
            type="button"
            onClick={() => void removeSelected()}
            disabled={selectedIds.length === 0 || deleting || removing.size > 0}
            className="min-h-9 text-[13px] text-text-2 underline underline-offset-[3px] hover:text-navy focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-navy disabled:cursor-not-allowed disabled:text-text-3 disabled:no-underline"
          >
            선택 삭제
          </button>
        </div>

        {groups.map((g) => {
          const sellerSelectable = g.items.filter(isSelectable);
          const sellerOn = sellerSelectable.length > 0 && sellerSelectable.every((i) => sel.has(i.cartItemId));
          const sq = quote.data?.sellerQuotes.find((s) => s.sellerId === g.sellerId);
          const showFoot = sq !== undefined && !stale && selectedIds.length > 0 && g.items.some((i) => sel.has(i.cartItemId));
          return (
            <section key={g.sellerId ?? "none"} aria-label={`${g.sellerName} 상품`} className="mt-4 rounded-lg border border-border">
              <div className="flex min-h-14 items-center justify-between gap-3 rounded-t-lg border-b border-border bg-surface px-5 py-2.5">
                <label className="flex min-h-9 cursor-pointer items-center gap-2 text-sm font-bold">
                  <input
                    type="checkbox"
                    className={check}
                    checked={sellerOn}
                    disabled={sellerSelectable.length === 0}
                    onChange={() => setSel(toggleSeller(items, sel, g.sellerId))}
                  />
                  {g.sellerName}
                </label>
                {g.sellerId !== null && <span className="text-xs text-text-2">판매자별로 따로 배송됩니다</span>}
              </div>
              {g.items.map((item) => (
                <CartLineItem
                  key={item.cartItemId}
                  item={item}
                  checked={sel.has(item.cartItemId)}
                  onToggle={() => setSel(toggleItem(items, sel, item.cartItemId))}
                  onRemove={() => void removeOne(item.cartItemId)}
                  sync={sync}
                  dirty={dirty.has(item.cartItemId)}
                  removing={removing.has(item.cartItemId)}
                />
              ))}
              {showFoot && (
                <div className="flex items-center justify-end gap-5 border-t border-border px-5 py-3.5 text-[13px] text-text-2">
                  <span>
                    선택 상품 <b className="ml-1.5 font-bold text-foreground"><Money value={sq.itemsAmount} /></b>
                  </span>
                  <span aria-hidden className="h-3 w-px bg-border" />
                  <span>
                    배송비{" "}
                    <b className="ml-1.5 font-bold text-foreground">
                      <Money value={sq.shippingFee} />
                    </b>
                  </span>
                </div>
              )}
            </section>
          );
        })}

        <p className="mt-4 text-xs text-text-2">
          판매 중지·품절·판매 종료된 상품은 주문할 수 없어 선택에서 빠집니다. 장바구니에서 삭제만 할 수 있습니다.
        </p>
      </div>

      <aside aria-label="결제 예정 금액" className="sticky top-24">
        <CartSummary selectedIds={selectedIds} quote={quote.data} stale={stale && selectedIds.length > 0} error={quote.error} onRetry={() => void quote.refetch()} />
      </aside>
    </div>
  );
}

export function CartPage() {
  const query = useCart();

  return (
    <div className="container pb-16">
      <Heading count={query.data && query.data.items.length > 0 ? query.data.items.length : undefined} />
      {query.isPending ? (
        <PageSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : query.data.items.length === 0 ? (
        <div className="border-b border-border border-t-2 border-t-navy">
          <EmptyState message="장바구니가 비어 있습니다" action={{ label: "상품 보러 가기", href: "/products" }} />
        </div>
      ) : (
        <CartBody items={query.data.items} />
      )}
    </div>
  );
}

