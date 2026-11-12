import { useCallback, useRef, useState, type BaseSyntheticEvent } from "react";
import type { FieldValues, UseFormReturn } from "react-hook-form";
import { isAnswered, newIdempotencyKey } from "@/api/idempotency";

/**
 * 제출마다 Idempotency-Key 를 붙이고 이중 제출을 막는다.
 * - submit 은 실패하면 반드시 던진다. 훅이 잡아 키를 정하고 opts.onError 를 부른 뒤 삼킨다(onSubmit·retry 는 reject 하지 않는다).
 * - 키 규칙: submit 이 성공하거나 서버가 답한 실패(4xx, isAnswered)면 키를 버린다(다음 제출은 새 키).
 *   응답을 받지 못한 실패(네트워크·5xx·처리 중 409 IDEMPOTENCY_IN_PROGRESS·그 밖의 오류)면 키를 유지해 같은 값 재제출·retry 가 같은 키를 쓴다.
 * - 값이 마지막 제출 이후 바뀌었으면 새 키. 폼 밖의 값(주문서의 쿠폰·포인트·기대 금액)도 요청 내용이면 opts.fingerprint 로 알린다
 *   (같은 키로 내용이 다른 요청이 나가면 서버가 옛 주문을 돌려줄 수 있다). 빈 문자열은 "지금은 알 수 없음"이라 직전 값을 그대로 쓴다.
 * - retry 는 응답 없이 실패한 직전 시도만 같은 키로 다시 보낸다(그 밖에는 아무것도 하지 않는다).
 * - 제출 중 두 번째 호출은 무시한다.
 */
export function useIdempotentSubmit<TInput extends FieldValues, TValues = TInput>(
  // zod transform 을 쓰는 폼은 입력(TInput)과 제출 값(TValues)이 다르다.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- RHF 의 TContext 기본값과 같다
  form: UseFormReturn<TInput, any, TValues>,
  submit: (values: TValues, key: string) => Promise<void>,
  opts?: { onError?: (e: unknown) => void | Promise<void>; fingerprint?: () => string },
): { onSubmit: (e?: BaseSyntheticEvent) => Promise<void>; submitting: boolean; retry: () => Promise<void> } {
  const [submitting, setSubmitting] = useState(false);
  const inFlight = useRef(false);
  // key 가 null 이면 직전 시도가 끝났다는 뜻이라 다음 제출은 새 키를 만들고 retry 는 아무것도 하지 않는다.
  const last = useRef<{ snapshot: string; key: string | null; values: TValues } | null>(null);
  const submitRef = useRef(submit);
  submitRef.current = submit;
  const onErrorRef = useRef(opts?.onError);
  onErrorRef.current = opts?.onError;
  const fingerprintRef = useRef(opts?.fingerprint);
  fingerprintRef.current = opts?.fingerprint;
  const lastFingerprint = useRef("");

  const run = useCallback(async (snapshot: string, values: TValues, key: string) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    last.current = { snapshot, key, values };
    try {
      await submitRef.current(values, key);
      last.current = { snapshot, key: null, values };
    } catch (e) {
      last.current = { snapshot, key: isAnswered(e) ? null : key, values };
      try {
        await onErrorRef.current?.(e);
      } catch (handlerError) {
        console.error("useIdempotentSubmit onError 처리 중 오류", handlerError);
      }
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
        const fp = fingerprintRef.current?.() ?? "";
        if (fp !== "") lastFingerprint.current = fp;
        const snapshot = fingerprintRef.current ? JSON.stringify([values, lastFingerprint.current]) : JSON.stringify(values);
        const prev = last.current;
        const key = prev && prev.snapshot === snapshot && prev.key ? prev.key : newIdempotencyKey();
        await run(snapshot, values, key);
      })(e);
    },
    [form, run],
  );

  const retry = useCallback(async () => {
    const prev = last.current;
    // 키가 없으면(직전 시도가 성공했거나 서버가 답했다) 다시 보낼 것이 없다.
    if (!prev?.key) return;
    await run(prev.snapshot, prev.values, prev.key);
  }, [run]);

  return { onSubmit, submitting, retry };
}
