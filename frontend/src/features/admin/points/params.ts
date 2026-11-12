import { parseId } from "@/lib/ids";

/** ?memberId= 를 회원 ID 로 읽는다. 양의 정수가 아니면 null(조회하지 않는다). */
export function parseMemberIdParam(sp: URLSearchParams): number | null {
  return parseId(sp.get("memberId"));
}
