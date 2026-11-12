import { useCallback, useRef, useState } from "react";
import { isAnswered, newIdempotencyKey } from "@/api/idempotency";

/**
 * 폼 없는 요청용 멱등 실행기. useIdempotentSubmit 과 같은 계약·키 규칙이다.
 * - action 은 실패하면 던진다. 훅이 잡아 키를 정하고 opts.onError 를 부른 뒤 삼킨다(run 은 reject 하지 않는다).
 * - 직전 시도가 응답 없이 실패했고 JSON.stringify(args) 가 같으면 같은 키, 그 밖에는 새 키.
 * - 처리 중 재호출은 무시한다.
 */
export function useIdempotentAction<TArgs>(
  action: (args: TArgs, key: string) => Promise<void>,
  opts?: { onError?: (e: unknown) => void | Promise<void> },
): { run: (args: TArgs) => Promise<void>; pending: boolean } {
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const pendingKey = useRef<{ snapshot: string; key: string } | null>(null);
  const actionRef = useRef(action);
  actionRef.current = action;
  const onErrorRef = useRef(opts?.onError);
  onErrorRef.current = opts?.onError;

  const run = useCallback(async (args: TArgs) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    const snapshot = JSON.stringify(args);
    const prev = pendingKey.current;
    const key = prev && prev.snapshot === snapshot ? prev.key : newIdempotencyKey();
    pendingKey.current = { snapshot, key };
    try {
      await actionRef.current(args, key);
      pendingKey.current = null;
    } catch (e) {
      if (isAnswered(e)) pendingKey.current = null;
      await onErrorRef.current?.(e);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }, []);

  return { run, pending };
}
