import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { useIdempotentAction } from "@/hooks/useIdempotentAction";

type A = { id: number };

function setup(action: (a: A, key: string) => Promise<void>, onError?: (e: unknown) => void) {
  return renderHook(() => useIdempotentAction<A>(action, onError ? { onError } : undefined));
}
const keysOf = (f: { mock: { calls: unknown[][] } }) => f.mock.calls.map((c) => c[1]);

describe("useIdempotentAction", () => {
  it("서버가_409로_답한_뒤_같은_인자로_다시_실행하면_새_키를_쓴다", async () => {
    const action = vi.fn(async () => {
      throw new ApiError(409, "STOCK_SOLD_OUT", "x", null);
    });
    const { result } = setup(action, () => {});
    await act(() => result.current.run({ id: 1 }));
    await act(() => result.current.run({ id: 1 }));
    expect(new Set(keysOf(action)).size).toBe(2);
  });

  it("성공한_뒤_같은_인자로_다시_실행하면_새_키를_쓴다", async () => {
    const action = vi.fn(async () => {});
    const { result } = setup(action);
    await act(() => result.current.run({ id: 1 }));
    await act(() => result.current.run({ id: 1 }));
    expect(new Set(keysOf(action)).size).toBe(2);
  });

  it("응답_없는_실패_뒤_같은_인자로_다시_실행하면_같은_키를_쓴다", async () => {
    const errors = [new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null), new ApiError(500, "INTERNAL_ERROR", "x", null), new TypeError("boom")];
    for (const e of errors) {
      const action = vi.fn(async () => {
        throw e;
      });
      const { result } = setup(action, () => {});
      await act(() => result.current.run({ id: 1 }));
      await act(() => result.current.run({ id: 1 }));
      expect(action).toHaveBeenCalledTimes(2);
      expect(new Set(keysOf(action)).size).toBe(1);
    }
  });

  it("처리_중_409_뒤_다시_보내면_같은_키를_쓴다", async () => {
    const action = vi.fn(async () => {
      throw new ApiError(409, "IDEMPOTENCY_IN_PROGRESS", "x", null);
    });
    const { result } = setup(action, () => {});
    await act(() => result.current.run({ id: 1 }));
    await act(() => result.current.run({ id: 1 }));
    expect(action).toHaveBeenCalledTimes(2);
    expect(new Set(keysOf(action)).size).toBe(1);
  });

  it("onError가_던져도_reject하지_않는다", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const action = vi.fn(async () => {
      throw new ApiError(422, "INVALID_REQUEST", "x", null);
    });
    const { result } = setup(action, async () => {
      throw new Error("handler");
    });
    await act(() => result.current.run({ id: 1 }));
    expect(result.current.pending).toBe(false);
    vi.restoreAllMocks();
  });

  it("던지면_onError를_부르고_run은_reject하지_않는다", async () => {
    const err = new ApiError(402, "INVALID_REQUEST", "x", null);
    const action = vi.fn(async () => {
      throw err;
    });
    const onError = vi.fn();
    const { result } = setup(action, onError);
    await act(() => result.current.run({ id: 1 }));
    expect(onError).toHaveBeenCalledWith(err);
    expect(result.current.pending).toBe(false);
  });

  it("인자가_다르면_새_키를_쓴다", async () => {
    const action = vi.fn(async () => {
      throw new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
    });
    const { result } = setup(action, () => {});
    await act(() => result.current.run({ id: 1 }));
    await act(() => result.current.run({ id: 2 }));
    expect(new Set(keysOf(action)).size).toBe(2);
  });

  it("처리_중_재호출은_무시한다", async () => {
    let release: () => void = () => {};
    const action = vi.fn(() => new Promise<void>((r) => (release = r)));
    const { result } = setup(action);
    let p1: Promise<void> = Promise.resolve();
    await act(async () => {
      p1 = result.current.run({ id: 1 });
      await vi.waitFor(() => expect(action).toHaveBeenCalledTimes(1));
    });
    expect(result.current.pending).toBe(true);
    await act(() => result.current.run({ id: 1 }));
    expect(action).toHaveBeenCalledTimes(1);
    await act(async () => {
      release();
      await p1;
    });
    expect(result.current.pending).toBe(false);
  });
});
