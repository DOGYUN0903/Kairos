"use client";

import { useSellerMe } from "@/features/seller/gate/useSellerMe";

/**
 * 등록·수정·브랜드 신청(canManage)과 주문 처리(canProcessOrders: 발송·배송 완료·항목 취소)는 APPROVED 판매자만.
 * SUSPENDED 는 서버가 두 경우 모두 409 SELLER_NOT_ACTIVE 로 막고 조회만 가능하다.
 */
export function useSellerCapabilities(): { canManage: boolean; canProcessOrders: boolean } {
  const { status } = useSellerMe();
  const approved = status === "APPROVED";
  return { canManage: approved, canProcessOrders: approved };
}
