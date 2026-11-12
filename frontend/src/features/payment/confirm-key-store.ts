import { newIdempotencyKey } from "@/api/idempotency";

// 결제 승인의 Idempotency-Key 는 paymentKey 마다 하나다.
// 새로고침·뒤로 가기로 승인 화면이 다시 떠도 같은 키를 보내야 서버가 저장한 결과를 재생하고 승인이 두 번 실행되지 않는다.
// 토큰이 아니므로 sessionStorage 에 둔다. storage 가 막혀 있으면 모듈 메모리로 대신한다(이 경우 새로고침에는 키가 바뀌지만,
// 같은 주문·같은 paymentKey 의 재승인은 서버가 현재 상태로 답한다).

const PREFIX = "kairos:confirm:";
const memory = new Map<string, string>();

function read(name: string): string | null {
  try {
    return sessionStorage.getItem(name);
  } catch {
    return null;
  }
}

function write(name: string, value: string): void {
  try {
    sessionStorage.setItem(name, value);
  } catch {
    // 메모리에만 남는다.
  }
}

export function confirmKeyFor(paymentKey: string): string {
  const name = PREFIX + paymentKey;
  const known = memory.get(name) ?? read(name);
  if (known) {
    memory.set(name, known);
    return known;
  }
  const key = newIdempotencyKey();
  memory.set(name, key);
  write(name, key);
  return key;
}
