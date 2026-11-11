export const IDEMPOTENCY_HEADER = "Idempotency-Key";

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
