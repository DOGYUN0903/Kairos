"use client";

import Link from "next/link";
import { StatusChip } from "@/components/common/StatusChip";
import type { CouponResult } from "@/features/coupons/api";
import { CouponCard, KindBadge } from "@/features/coupons/components/CouponCard";
import { isExpiringSoon } from "@/features/coupons/expiry";
import { formatDate } from "@/lib/datetime";

const soonBadge = "inline-flex h-5 items-center rounded-[2px] bg-[#FFF6DD] px-1.5 text-[11px] font-bold leading-none text-[#7A5600]";

/** 내 쿠폰 한 장. 내 쿠폰에는 발급 주체·적용 대상 정보가 없어 종류만 보인다. nowMs 는 "곧 만료" 비교 기준. */
export function MyCouponCard({ coupon, nowMs }: { coupon: CouponResult; nowMs: number }) {
  const soon = coupon.status === "AVAILABLE" && isExpiringSoon(coupon.expiresAt, nowMs);
  const period =
    coupon.status === "USED" ? (
      `사용일 ${coupon.usedAt ? formatDate(coupon.usedAt) : "-"}`
    ) : coupon.status === "EXPIRED" ? (
      `${formatDate(coupon.expiresAt)} 만료`
    ) : (
      <span className={soon ? "font-medium text-danger" : undefined}>{formatDate(coupon.expiresAt)}까지</span>
    );
  return (
    <CouponCard
      name={coupon.name}
      terms={coupon}
      done={coupon.status !== "AVAILABLE"}
      tags={
        <>
          <KindBadge couponType={coupon.couponType} />
          {soon && <span className={soonBadge}>곧 만료</span>}
        </>
      }
      period={period}
      action={
        coupon.status === "USED" ? (
          coupon.usedOrderId != null ? (
            <Link className="btn btn-ghost btn-sm" href={`/my/orders/${coupon.usedOrderId}`}>
              주문 보기
            </Link>
          ) : null
        ) : coupon.status === "EXPIRED" ? (
          <StatusChip kind="coupon" status="EXPIRED" />
        ) : null
      }
    />
  );
}
