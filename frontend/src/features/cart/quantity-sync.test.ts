import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { isQuoteStale, QuantitySync } from "@/features/cart/quantity-sync";

function deferred() {
  let resolve!: () => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<void>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("수량 동기화", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("입력을_300ms_모아_마지막_값만_보낸다", async () => {
    const send = vi.fn().mockResolvedValue(undefined);
    const sync = new QuantitySync(send);
    sync.set(1, 2, 1);
    await vi.advanceTimersByTimeAsync(200);
    sync.set(1, 3, 1);
    await vi.advanceTimersByTimeAsync(299);
    expect(send).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(send).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenCalledWith(1, 3);
  });

  it("같은_행의_수량_변경은_한_번에_하나만_보내고_마지막_값으로_끝난다", async () => {
    const first = deferred();
    const send = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue(undefined);
    const sync = new QuantitySync(send);
    sync.set(1, 2, 1);
    await vi.advanceTimersByTimeAsync(300);
    expect(send).toHaveBeenCalledTimes(1);

    sync.set(1, 5, 1); // 첫 PATCH 가 진행 중
    await vi.advanceTimersByTimeAsync(300);
    expect(send).toHaveBeenCalledTimes(1); // 동시에 두 개를 보내지 않는다

    first.resolve();
    await vi.advanceTimersByTimeAsync(0);
    expect(send).toHaveBeenCalledTimes(2);
    expect(send).toHaveBeenLastCalledWith(1, 5);
    await vi.advanceTimersByTimeAsync(0);
    expect(sync.dirtyIds().size).toBe(0);
  });

  it("다른_행은_서로_기다리지_않는다", async () => {
    const never = deferred();
    const send = vi.fn().mockReturnValueOnce(never.promise).mockResolvedValue(undefined);
    const sync = new QuantitySync(send);
    sync.set(1, 2, 1);
    sync.set(2, 4, 3);
    await vi.advanceTimersByTimeAsync(300);
    expect(send).toHaveBeenCalledTimes(2);
  });

  it("저장되지_않은_수량이_있으면_주문할_수_없다", async () => {
    const pending = deferred();
    const sync = new QuantitySync(() => pending.promise);
    expect(isQuoteStale({ fetching: false, placeholder: false, unsavedCount: sync.dirtyIds().size })).toBe(false);

    sync.set(1, 2, 1); // 디바운스 대기
    expect(isQuoteStale({ fetching: false, placeholder: false, unsavedCount: sync.dirtyIds().size })).toBe(true);

    await vi.advanceTimersByTimeAsync(300); // PATCH 진행 중
    expect(isQuoteStale({ fetching: false, placeholder: false, unsavedCount: sync.dirtyIds().size })).toBe(true);

    pending.resolve();
    await vi.advanceTimersByTimeAsync(0);
    expect(isQuoteStale({ fetching: false, placeholder: false, unsavedCount: sync.dirtyIds().size })).toBe(false);
    expect(isQuoteStale({ fetching: true, placeholder: false, unsavedCount: 0 })).toBe(true);
    expect(isQuoteStale({ fetching: false, placeholder: true, unsavedCount: 0 })).toBe(true);
  });

  it("언마운트_때_대기_중인_수량을_보낸다", async () => {
    const send = vi.fn().mockResolvedValue(undefined);
    const sync = new QuantitySync(send);
    sync.set(1, 7, 1);
    sync.flushAll();
    expect(send).toHaveBeenCalledWith(1, 7);
    await vi.advanceTimersByTimeAsync(1000);
    expect(send).toHaveBeenCalledTimes(1); // 타이머가 다시 보내지 않는다
  });

  it("서버_값으로_되돌린_입력은_보내지_않는다", async () => {
    const send = vi.fn().mockResolvedValue(undefined);
    const sync = new QuantitySync(send);
    sync.set(1, 4, 2);
    sync.set(1, 2, 2);
    await vi.advanceTimersByTimeAsync(300);
    expect(send).not.toHaveBeenCalled();
    expect(sync.dirtyIds().size).toBe(0);
  });

  it("실패하면_저장되지_않은_상태에서_빠진다", async () => {
    const send = vi.fn().mockRejectedValue(new Error("fail"));
    const sync = new QuantitySync(send);
    sync.set(1, 4, 2);
    await vi.advanceTimersByTimeAsync(300);
    expect(sync.dirtyIds().size).toBe(0);
  });
});
