/** 빈 값(null·undefined·빈 문자열)을 건너뛰고 " · " 로 잇는다. 옵션이 없는 상품에서 구분점이 홀로 남지 않게 한다. */
export function joinDot(...parts: Array<string | null | undefined>): string {
  return parts.filter((p): p is string => Boolean(p)).join(" · ");
}
