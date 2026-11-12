/** ?page= 값(0부터). 숫자가 아니거나 음수·소수면 0. */
export function parsePage(raw: string | null): number {
  return raw !== null && /^\d{1,6}$/.test(raw) ? Number(raw) : 0;
}
