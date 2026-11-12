"use client";

import { useQueryClient } from "@tanstack/react-query";
import { claimCoupon, type CouponPolicyResult } from "@/features/coupons/api";
import { claimOutcome } from "@/features/coupons/claim-error";
import { useIdempotentAction } from "@/hooks/useIdempotentAction";
import { toast } from "@/hooks/use-toast";
import { formatMonthDay } from "@/lib/datetime";
import { queryKeys } from "@/lib/query-keys";

/** 쿠폰 받기 버튼. 카드마다 자기 멱등 실행기를 가져 받는 중인 카드만 비활성이 된다. */
export function ClaimButton({ policy }: { policy: Pick<CouponPolicyResult, "policyId" | "claimed"> }) {
  const qc = useQueryClient();
  const { run, pending } = useIdempotentAction(
    async (policyId: number, key: string) => {
      const res = await claimCoupon(policyId, key);
      toast({ title: `쿠폰을 받았습니다. ${formatMonthDay(res.expiresAt)}까지 사용할 수 있습니다.` });
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.coupons.policiesRoot }),
        qc.invalidateQueries({ queryKey: queryKeys.coupons.mineRoot }),
        qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes }),
      ]);
    },
    {
      onError: async (e) => {
        const { message, refetch } = claimOutcome(e);
        toast(refetch ? { title: message } : { variant: "destructive", title: message });
        if (refetch) await qc.invalidateQueries({ queryKey: queryKeys.coupons.policiesRoot });
      },
    },
  );

  if (policy.claimed) {
    return (
      <button type="button" className="btn btn-sm" disabled>
        받음
      </button>
    );
  }
  return (
    <button type="button" className="btn btn-primary btn-sm" disabled={pending} onClick={() => void run(policy.policyId)}>
      {pending ? "받는 중" : "쿠폰 받기"}
    </button>
  );
}
