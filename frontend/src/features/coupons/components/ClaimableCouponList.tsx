import { ClaimButton } from "@/features/coupons/components/ClaimButton";
import { CouponCard, IssuerBadge, KindBadge } from "@/features/coupons/components/CouponCard";
import type { CouponPolicyResult } from "@/features/coupons/api";
import { formatDate } from "@/lib/datetime";
import { cn } from "@/lib/utils";

/** 받을 수 있는 쿠폰 카드 한 장. 상품 하나 대상이면 대상 상품 이름을 보인다. */
export function ClaimableCouponCard({ policy, small }: { policy: CouponPolicyResult; small?: boolean }) {
  const target =
    policy.targetType === "PRODUCT" && policy.targetProductId != null
      ? `대상 상품 · ${policy.targetProductName ?? `상품 #${policy.targetProductId}`}`
      : undefined;
  return (
    <CouponCard
      name={policy.name}
      terms={policy}
      tags={
        <>
          <IssuerBadge issuerType={policy.issuerType} />
          <KindBadge couponType={policy.couponType} />
        </>
      }
      period={`${formatDate(policy.validUntil)}까지`}
      {...(target !== undefined ? { targetProductName: target } : {})}
      action={<ClaimButton policy={policy} />}
      {...(small !== undefined ? { small } : {})}
    />
  );
}

export function ClaimableCouponList({
  policies,
  small,
  className,
}: {
  policies: CouponPolicyResult[];
  small?: boolean;
  className?: string;
}) {
  return (
    <ul className={cn("flex flex-col gap-2", className)}>
      {policies.map((p) => (
        <li key={p.policyId}>
          <ClaimableCouponCard policy={p} {...(small !== undefined ? { small } : {})} />
        </li>
      ))}
    </ul>
  );
}
