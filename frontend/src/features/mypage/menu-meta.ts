import { formatPoints } from "@/lib/money";

const num = new Intl.NumberFormat("ko-KR");

/** 마이페이지 메뉴 오른쪽 값. 서버 값을 그대로 문구로만 바꾼다(없는 항목은 빠진다). */
export function menuMetaLabels(v: { coupons?: number; points?: number; likes?: number }): Record<string, string> {
  const out: Record<string, string> = {};
  if (v.coupons !== undefined) out["/my/coupons"] = `${num.format(v.coupons)}장`;
  if (v.points !== undefined) out["/my/points"] = formatPoints(v.points);
  if (v.likes !== undefined) out["/my/likes"] = num.format(v.likes);
  return out;
}
