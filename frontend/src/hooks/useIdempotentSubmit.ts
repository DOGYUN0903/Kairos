import { useCallback, useRef, useState, type BaseSyntheticEvent } from "react";
import type { FieldValues, UseFormReturn } from "react-hook-form";
import { newIdempotencyKey } from "@/api/idempotency";

/**
 * 제출마다 Idempotency-Key 를 붙이고 이중 제출을 막는다.
 * - 값이 마지막 제출 이후 바뀌었으면 새 키, 그대로면 같은 키(같은 시도의 재전송).
 * - retry 는 마지막 값·키로 다시 호출한다.
 * - 제출 중 두 번째 호출은 무시한다.
 * submit 의 에러 처리는 호출자가 submit 안에서 한다(여기서는 그대로 전파).
 */
export function useIdempotentSubmit<TInput extends FieldValues, TValues = TInput>(
  // zod transform 을 쓰는 폼은 입력(TInput)과 제출 값(TValues)이 다르다.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- RHF 의 TContext 기본값과 같다
  form: UseFormReturn<TInput, any, TValues>,
  submit: (values: TValues, key: string) => Promise<void>,
): { onSubmit: (e?: BaseSyntheticEvent) => Promise<void>; submitting: boolean; retry: () => Promise<void> } {
  const [submitting, setSubmitting] = useState(false);
  const inFlight = useRef(false);
  const last = useRef<{ snapshot: string; key: string; values: TValues } | null>(null);
  const submitRef = useRef(submit);
  submitRef.current = submit;

  const run = useCallback(async (values: TValues, key: string) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    try {
      await submitRef.current(values, key);
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }, []);

  const onSubmit = useCallback(
    async (e?: BaseSyntheticEvent) => {
      if (inFlight.current) {
        e?.preventDefault?.();
        return;
      }
      await form.handleSubmit(async (values) => {
        const snapshot = JSON.stringify(values);
        const prev = last.current;
        const key = prev && prev.snapshot === snapshot ? prev.key : newIdempotencyKey();
        last.current = { snapshot, key, values };
        await run(values, key);
      })(e);
    },
    [form, run],
  );

  const retry = useCallback(async () => {
    const prev = last.current;
    if (!prev) return;
    await run(prev.values, prev.key);
  }, [run]);

  return { onSubmit, submitting, retry };
}
