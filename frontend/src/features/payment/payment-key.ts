export type MockOutcome = "approve" | "reject" | "unknown" | "lost";

/** 모의 PG(MockPgClient)가 결과를 고르는 paymentKey 접두사. 실제 값은 features/demo 에만 있다. */
export type MockOutcomePrefixes = Record<Exclude<MockOutcome, "approve">, string>;

/**
 * 모의 결제의 paymentKey. 정상은 `mock-{uuid}`.
 * 실패 접두사는 데모 빌드에서만 불러오는 MockOutcomePicker 가 넘겨준다.
 * 접두사를 받지 못하면(운영 번들) 어떤 결과를 고르든 정상 키다.
 */
export function mockPaymentKey(outcome: MockOutcome, uuid: string, prefixes?: MockOutcomePrefixes): string {
  if (outcome === "approve" || !prefixes) return `mock-${uuid}`;
  return `${prefixes[outcome]}${uuid}`;
}
