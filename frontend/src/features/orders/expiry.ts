/** 만료된 결제 대기 주문을 서버가 취소할 때까지 다시 확인하는 간격과 한도. 만료 작업은 자체 주기로 돌아서 바로 바뀌지 않는다. */
export const EXPIRED_RECHECK_MS = 10000;
export const EXPIRED_RECHECK_LIMIT_MS = 120000;

/** 결제 대기 주문의 기한이 지났는가(이 순간부터 서버가 취소하기 전까지의 상태). */
export function isPastExpiry(status: string, secondsLeft: number): boolean {
  return status === "PENDING_PAYMENT" && secondsLeft === 0;
}

/** 기한을 넘는 순간(이전엔 아니었고 지금은 맞다)에만 한 번 다시 읽는다. */
export function shouldRefetchAtExpiry(wasPast: boolean, isPast: boolean): boolean {
  return !wasPast && isPast;
}

/** 기한이 지난 결제 대기 주문은 10초마다, 기한을 넘은 뒤 2분까지만 다시 확인한다. 그 밖에는 false. */
export function expiredRecheckInterval(isPast: boolean, sincePastMs: number): number | false {
  return isPast && sincePastMs < EXPIRED_RECHECK_LIMIT_MS ? EXPIRED_RECHECK_MS : false;
}
