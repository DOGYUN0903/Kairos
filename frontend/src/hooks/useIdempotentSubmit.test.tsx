import { act, renderHook } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";

type V = { name: string };

function setup(submit: (v: V, key: string) => Promise<void>, onError?: (e: unknown) => void, fingerprint?: () => string) {
  return renderHook(() => {
    const form = useForm<V>({ defaultValues: { name: "a" } });
    return { form, ...useIdempotentSubmit(form, submit, onError || fingerprint ? { onError, fingerprint } : undefined) };
  });
}

describe("useIdempotentSubmit", () => {
  const keysOf = (submit: { mock: { calls: unknown[][] } }) => submit.mock.calls.map((c) => c[1]);

  it("서버가_409로_답한_뒤_같은_값으로_다시_제출하면_새_키를_쓴다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(409, "STOCK_SOLD_OUT", "x", null);
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    expect(new Set(keysOf(submit)).size).toBe(2);
  });

  it("성공한_뒤_같은_값으로_다시_제출하면_새_키를_쓴다", async () => {
    const submit = vi.fn(async () => {});
    const { result } = setup(submit);
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    expect(new Set(keysOf(submit)).size).toBe(2);
  });

  it("네트워크_오류_뒤_같은_값으로_다시_제출하면_같은_키를_쓴다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    expect(submit).toHaveBeenCalledTimes(2);
    expect(new Set(keysOf(submit)).size).toBe(1);
  });

  it("5xx_뒤_retry는_같은_키를_쓴다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(503, "INTERNAL_ERROR", "x", null);
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    await act(() => result.current.retry());
    expect(submit).toHaveBeenCalledTimes(2);
    expect(new Set(keysOf(submit)).size).toBe(1);
  });

  it("ApiError가_아닌_오류_뒤에도_같은_키를_쓴다", async () => {
    const submit = vi.fn(async () => {
      throw new TypeError("boom");
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    expect(submit).toHaveBeenCalledTimes(2);
    expect(new Set(keysOf(submit)).size).toBe(1);
  });

  it("네트워크_오류_뒤_submit이_일반_오류를_던져도_다음_제출은_같은_키를_쓴다", async () => {
    // 주문서: 시도1 이 응답 없이 끝난 뒤(서버에 주문이 생겼을 수 있다) 시도2 가 요청을 보내지 않고 끝나도 키가 버려지면 안 된다.
    const sent: string[] = [];
    let attempt = 0;
    const submit = vi.fn(async (_v: V, key: string) => {
      attempt += 1;
      if (attempt === 1) {
        sent.push(key);
        throw new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
      }
      if (attempt === 2) throw new Error("견적이 확정되지 않았습니다");
      sent.push(key);
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    expect(submit).toHaveBeenCalledTimes(3);
    expect(sent).toHaveLength(2);
    expect(sent[1]).toBe(sent[0]);
  });

  it("처리_중_409_뒤_다시_보내면_같은_키를_쓴다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(409, "IDEMPOTENCY_IN_PROGRESS", "x", null);
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    expect(submit).toHaveBeenCalledTimes(2);
    expect(new Set(keysOf(submit)).size).toBe(1);
  });

  it("onError가_던져도_reject하지_않는다", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const submit = vi.fn(async () => {
      throw new ApiError(422, "INVALID_REQUEST", "x", null);
    });
    const { result } = setup(submit, () => {
      throw new Error("handler");
    });
    await act(() => result.current.onSubmit());
    await act(() => result.current.retry());
    expect(result.current.submitting).toBe(false);
    vi.restoreAllMocks();
  });

  it("답을_받은_뒤의_retry는_아무것도_보내지_않는다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(422, "INVALID_REQUEST", "x", null);
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    await act(() => result.current.retry());
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it("submit이_던지면_onError를_부르고_onSubmit은_reject하지_않는다", async () => {
    const err = new ApiError(503, "INTERNAL_ERROR", "x", null);
    const submit = vi.fn(async () => {
      throw err;
    });
    const onError = vi.fn();
    const { result } = setup(submit, onError);
    await act(() => result.current.onSubmit());
    await act(() => result.current.retry());
    expect(onError).toHaveBeenCalledTimes(2);
    expect(onError).toHaveBeenCalledWith(err);
    expect(result.current.submitting).toBe(false);
  });

  it("값을_고치면_새_키를_쓴다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
    });
    const { result } = setup(submit, () => {});
    await act(() => result.current.onSubmit());
    act(() => result.current.form.setValue("name", "b", { shouldDirty: true }));
    await act(() => result.current.onSubmit());
    const [first, second] = submit.mock.calls.map((c) => c as unknown[]);
    expect(second?.[0]).toEqual({ name: "b" });
    expect(second?.[1]).not.toBe(first?.[1]);
  });

  it("제출_중_두_번째_호출은_무시한다", async () => {
    let release: () => void = () => {};
    const submit = vi.fn(() => new Promise<void>((r) => (release = r)));
    const { result } = setup(submit);
    let p1: Promise<void> = Promise.resolve();
    await act(async () => {
      p1 = result.current.onSubmit();
      await vi.waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
    });
    expect(result.current.submitting).toBe(true);
    await act(() => result.current.onSubmit());
    await act(() => result.current.retry());
    expect(submit).toHaveBeenCalledTimes(1);
    await act(async () => {
      release();
      await p1;
    });
    expect(result.current.submitting).toBe(false);
  });

  it("응답을_못_받은_뒤_폼_밖_내용이_바뀌면_새_키를_쓰고_같으면_같은_키를_쓴다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
    });
    let fp = "A";
    const { result } = setup(submit, () => {}, () => fp);
    await act(() => result.current.onSubmit());
    await act(() => result.current.onSubmit());
    fp = "B";
    await act(() => result.current.onSubmit());
    expect(keysOf(submit)[0]).toBe(keysOf(submit)[1]);
    expect(keysOf(submit)[2]).not.toBe(keysOf(submit)[0]);
  });

  it("지문이_비어_있으면_직전_지문으로_보아_키를_유지한다", async () => {
    const submit = vi.fn(async () => {
      throw new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null);
    });
    let fp = "A";
    const { result } = setup(submit, () => {}, () => fp);
    await act(() => result.current.onSubmit());
    fp = "";
    await act(() => result.current.onSubmit());
    expect(keysOf(submit)[0]).toBe(keysOf(submit)[1]);
  });
});
