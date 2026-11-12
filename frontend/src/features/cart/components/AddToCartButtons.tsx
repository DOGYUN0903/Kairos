"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/auth/useAuth";
import { ToastAction } from "@/components/ui/toast";
import { useAddCartItem } from "@/features/cart/api";
import { checkoutHref } from "@/features/checkout/checkout-params";
import { toast } from "@/hooks/use-toast";

/** 장바구니 / 바로 구매. skuId 가 null 이면(옵션 미선택·품절 조합) 둘 다 비활성. */
export function AddToCartButtons({ skuId, quantity, disabled }: { skuId: number | null; quantity: number; disabled: boolean }) {
  const { status } = useAuth();
  const router = useRouter();
  const add = useAddCartItem({
    onSuccess: () =>
      toast({
        title: "장바구니에 담았습니다",
        action: (
          <ToastAction altText="장바구니 보기" asChild>
            <Link href="/cart">장바구니 보기</Link>
          </ToastAction>
        ),
      }),
  });
  const off = disabled || skuId === null || status === "loading";

  const goLogin = (next: string) => router.push(`/login?next=${encodeURIComponent(next)}`);

  const onAdd = () => {
    if (off || skuId === null) return;
    if (status === "anonymous") return goLogin(window.location.pathname + window.location.search);
    void add.run({ skuId, quantity });
  };
  const onBuy = () => {
    if (off || skuId === null) return;
    const href = checkoutHref.direct(skuId, quantity);
    if (status === "anonymous") return goLogin(href);
    router.push(href);
  };

  return (
    <div className="grid grid-cols-2 gap-2">
      <button type="button" className="btn btn-lg" disabled={off || add.pending} onClick={onAdd}>
        장바구니
      </button>
      <button type="button" className="btn btn-primary btn-lg" disabled={off} onClick={onBuy}>
        바로 구매
      </button>
    </div>
  );
}
