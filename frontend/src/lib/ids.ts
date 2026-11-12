const POSITIVE_INT = /^[1-9]\d*$/;

/**
 * 주소·저장소에서 온 문자열을 ID 로 읽는다. 양의 정수 표기이고 Number 로 정확히 표현될 때만(Number.isSafeInteger) 숫자를 준다.
 * 자릿수만 세면 2^53 이상인 값이 통과해, 조회가 꺼진 채 화면이 스켈레톤에서 멈춘다.
 */
export function parseId(raw: string | null | undefined): number | null {
  if (raw === null || raw === undefined || !POSITIVE_INT.test(raw)) return null;
  const id = Number(raw);
  return Number.isSafeInteger(id) ? id : null;
}
