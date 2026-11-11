"use client";

import { useSellerMe } from "@/features/seller/gate/useSellerMe";

/** 등록·수정·브랜드 신청은 APPROVED 판매자만. SUSPENDED 는 조회만 가능하다. */
export function useSellerCapabilities(): { canManage: boolean } {
  const { status } = useSellerMe();
  return { canManage: status === "APPROVED" };
}
