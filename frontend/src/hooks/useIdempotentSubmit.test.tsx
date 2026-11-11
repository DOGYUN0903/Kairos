import { act, renderHook } from "@testing-library/react";
import { useForm } from "react-hook-form";
import { describe, expect, it, vi } from "vitest";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";

type V = { name: string };

function setup(submit: (v: V, key: string) => Promise<void>) {
  return renderHook(() => {
    const form = useForm<V>({ defaultValues: { name: "a" } });
    return { form, ...useIdempotentSubmit(form, submit) };
  });
}

describe("useIdempotentSubmit", () => {
  it("같은_시도의_retry는_같은_키", async () => {
    const submit = vi.fn(async () => {});
    const { result } = setup(submit);
    await act(() => result.current.onSubmit());
    await act(() => result.current.retry());
    await act(() => result.current.onSubmit());
    expect(submit).toHaveBeenCalledTimes(3);
    const keys = submit.mock.calls.map((c) => (c as unknown[])[1]);
    expect(new Set(keys).size).toBe(1);
  });

  it("값을_고친_뒤_제출하면_새_키", async () => {
    const submit = vi.fn(async () => {});
    const { result } = setup(submit);
    await act(() => result.current.onSubmit());
    act(() => result.current.form.setValue("name", "b", { shouldDirty: true }));
    await act(() => result.current.onSubmit());
    const [first, second] = submit.mock.calls.map((c) => c as unknown[]);
    expect(second?.[0]).toEqual({ name: "b" });
    expect(second?.[1]).not.toBe(first?.[1]);
  });

  it("제출_중_두_번째_제출은_무시", async () => {
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
});
