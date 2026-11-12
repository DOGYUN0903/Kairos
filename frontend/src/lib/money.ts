// 금액은 원 단위 정수만 다룬다. 부동소수 연산 금지. 나눗셈·나머지는 이 파일에서만 허용된다(ESLint).

export function assertSafeInt(n: number, label = "금액"): number {
  if (Number.isSafeInteger(n)) return n;
  if (process.env.NODE_ENV !== "production") {
    throw new Error(`${label}은(는) 안전한 정수여야 합니다: ${n}`);
  }
  return Number.NaN;
}

const krw = new Intl.NumberFormat("ko-KR");

export function formatKrw(n: number): string {
  const v = assertSafeInt(n);
  if (Number.isNaN(v)) return "금액 오류";
  return `${krw.format(v)}원`;
}

export function discountRate(originalPrice: number, salePrice: number): number {
  const orig = assertSafeInt(originalPrice, "정가");
  const sale = assertSafeInt(salePrice, "판매가");
  if (!(orig > 0)) throw new Error(`정가는 0보다 커야 합니다: ${originalPrice}`);
  return Math.floor(((orig - sale) * 100) / orig);
}

/** 단가 = 판매가 + 옵션 추가금(서버 값 두 개의 덧셈). */
export function unitPrice(salePrice: number, extraPrice: number): number {
  return assertSafeInt(assertSafeInt(salePrice, "판매가") + assertSafeInt(extraPrice, "추가금"), "단가");
}

export function lineTotal(salePrice: number, extraPrice: number, quantity: number): number {
  const total =
    (assertSafeInt(salePrice, "판매가") + assertSafeInt(extraPrice, "추가금")) * assertSafeInt(quantity, "수량");
  return assertSafeInt(total, "합계");
}

export function parseWon(input: string): number | null {
  const trimmed = input.trim();
  if (!/^[0-9][0-9,]*$/.test(trimmed)) return null;
  const n = Number(trimmed.replaceAll(",", ""));
  return Number.isSafeInteger(n) ? n : null;
}

export function formatBp(bp: number): string {
  const v = assertSafeInt(bp, "bp");
  if (Number.isNaN(v)) return "비율 오류";
  const whole = Math.floor(v / 100);
  const rest = v % 100;
  if (rest === 0) return `${whole}%`;
  return `${whole}.${String(rest).padStart(2, "0").replace(/0$/, "")}%`;
}

/** threshold 까지 남은 금액. 이미 넘었으면 0. */
export function amountUntil(threshold: number, amount: number): number {
  return Math.max(assertSafeInt(threshold, "기준 금액") - assertSafeInt(amount), 0);
}

function positiveDivisor(d: number): number {
  if (!Number.isSafeInteger(d) || d <= 0) throw new Error(`제수는 0보다 큰 안전한 정수여야 합니다: ${d}`);
  return d;
}

/** 안전 정수 전용 내림 나눗셈(시간 계산 등 금액 밖에서도 ESLint 가 막는 `/` 대신 쓴다). */
export function floorDiv(n: number, d: number): number {
  return Math.floor(assertSafeInt(n, "피제수") / positiveDivisor(d));
}

/** 안전 정수 전용 나머지(항상 0 이상 d 미만, d > 0). */
export function mod(n: number, d: number): number {
  const nn = assertSafeInt(n, "피제수");
  const dd = positiveDivisor(d);
  return ((nn % dd) + dd) % dd;
}
