"use client";

import { useMyCoupons } from "@/features/coupons/api";
import { useMyLikes } from "@/features/likes/api";
import { menuMetaLabels } from "@/features/mypage/menu-meta";
import { usePointSummary } from "@/features/points/api";

/** 메뉴 개수 표시. 읽는 중이거나 실패한 항목은 비워 둔다(0 으로 채우지 않는다). 기존 키를 써서 기존 무효화가 갱신한다. */
export function useMyMenuMeta(): Record<string, string> {
  const coupons = useMyCoupons({ status: "AVAILABLE", page: 0, size: 1 });
  const points = usePointSummary();
  const likes = useMyLikes(0, 1);
  return menuMetaLabels({
    ...(coupons.data ? { coupons: coupons.data.totalElements } : {}),
    ...(points.data ? { points: points.data.balance } : {}),
    ...(likes.data ? { likes: likes.data.totalElements } : {}),
  });
}
