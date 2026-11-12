"use client";

import { useEffect, useRef, useState } from "react";
import { useChangeCartQuantity } from "@/features/cart/api";

export type QuantitySender = (cartItemId: number, quantity: number) => Promise<void>;

type Row = { desired: number; baseline: number; timer: ReturnType<typeof setTimeout> | null; inFlight: number | null };

/**
 * 수량 변경을 행마다 모아(디바운스) 보내고 직렬화한다.
 * - 같은 행의 PATCH 는 동시에 하나만 나간다. 진행 중에 값이 바뀌면 끝난 뒤 마지막 값을 보낸다(last write wins).
 * - 행은 디바운스 대기 중이거나 PATCH 가 진행 중이면 "저장되지 않음(dirty)"이다.
 * - flushAll 은 대기 중인 값을 바로 보낸다(언마운트용).
 * sender 는 실패하면 reject 해도 된다(알림은 호출 쪽이 한다). 실패한 값은 버리고 서버 값으로 돌아간다.
 */
export class QuantitySync {
  private rows = new Map<number, Row>();
  private listeners = new Set<() => void>();

  constructor(
    private readonly send: QuantitySender,
    private readonly debounceMs = 300,
  ) {}

  set(cartItemId: number, quantity: number, baseline: number): void {
    const row = this.rows.get(cartItemId) ?? { desired: quantity, baseline, timer: null, inFlight: null };
    row.desired = quantity;
    if (row.inFlight === null) row.baseline = baseline;
    if (row.timer !== null) clearTimeout(row.timer);
    row.timer = setTimeout(() => {
      row.timer = null;
      this.run(cartItemId);
    }, this.debounceMs);
    this.rows.set(cartItemId, row);
    this.notify();
  }

  dirtyIds(): Set<number> {
    return new Set(this.rows.keys());
  }

  flushAll(): void {
    for (const [id, row] of this.rows) {
      if (row.timer === null) continue;
      clearTimeout(row.timer);
      row.timer = null;
      this.run(id);
    }
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    for (const l of this.listeners) l();
  }

  private run(id: number): void {
    const row = this.rows.get(id);
    if (!row || row.inFlight !== null) return; // 진행 중이면 끝난 뒤 이어서 보낸다.
    if (row.desired === row.baseline) {
      this.rows.delete(id);
      this.notify();
      return;
    }
    const sent = row.desired;
    row.inFlight = sent;
    this.send(id, sent).then(
      () => this.settle(id, row, sent, true),
      () => this.settle(id, row, sent, false),
    );
  }

  private settle(id: number, row: Row, sent: number, ok: boolean): void {
    row.inFlight = null;
    if (ok) row.baseline = sent;
    if (row.timer !== null) return; // 새 입력이 대기 중이면 그 타이머가 이어서 보낸다.
    if (row.desired !== sent && row.desired !== row.baseline) {
      this.run(id);
      return;
    }
    this.rows.delete(id);
    this.notify();
  }
}

/** 견적이 낡았는가: 견적을 새로 받는 중이거나, 저장되지 않은 수량이 있을 때. */
export function isQuoteStale(state: { fetching: boolean; placeholder: boolean; unsavedCount: number }): boolean {
  return state.fetching || state.placeholder || state.unsavedCount > 0;
}

export function useQuantitySync(): { sync: QuantitySync; dirty: ReadonlySet<number> } {
  const change = useChangeCartQuantity();
  const mutateRef = useRef(change.mutateAsync);
  mutateRef.current = change.mutateAsync;
  const ref = useRef<QuantitySync | null>(null);
  ref.current ??= new QuantitySync((cartItemId, quantity) => mutateRef.current({ cartItemId, quantity }).then(() => undefined));
  const sync = ref.current;
  const [dirty, setDirty] = useState<ReadonlySet<number>>(() => new Set());

  useEffect(() => {
    const off = sync.subscribe(() => setDirty(sync.dirtyIds()));
    return () => {
      off();
      sync.flushAll();
    };
  }, [sync]);

  return { sync, dirty };
}
