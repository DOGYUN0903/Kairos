"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { CircleAlert } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { FormNotice } from "@/features/auth/components/FormNotice";
import { applyFormError } from "@/features/auth/apply-form-error";
import { useCart } from "@/features/cart/api";
import { useCreateOrder, useQuote } from "@/features/checkout/api";
import { parseCheckoutParams, resolveCheckoutItems, type CheckoutItem, type CheckoutParams } from "@/features/checkout/checkout-params";
import { CheckoutSummary } from "@/features/checkout/components/CheckoutSummary";
import { OrderItems } from "@/features/checkout/components/OrderItems";
import { ShippingAddressForm } from "@/features/checkout/components/ShippingAddressForm";
import { shippingAddressSchema, type ShippingAddressInput, type ShippingAddressValues } from "@/features/checkout/schemas";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

function Heading() {
  return (
    <div className="mb-6 mt-9 flex items-end justify-between gap-4">
      <h1 className="text-[26px] font-bold tracking-tight">주문서</h1>
      <ol aria-label="구매 단계" className="flex items-center gap-2.5 text-[13px] text-text-3">
        <li>장바구니</li>
        <li aria-hidden>›</li>
        <li aria-current="step" className="font-bold text-navy">
          주문서
        </li>
        <li aria-hidden>›</li>
        <li>주문 완료</li>
      </ol>
    </div>
  );
}

export function CheckoutPage() {
  const router = useRouter();
  const sp = useSearchParams();
  const parsed = useMemo(() => parseCheckoutParams(new URLSearchParams(sp.toString())), [sp]);

  useEffect(() => {
    if (!parsed) router.replace("/cart");
  }, [parsed, router]);

  return (
    <div className="container pb-16">
      <Heading />
      {parsed ? <CheckoutResolve parsed={parsed} /> : <PageSkeleton />}
    </div>
  );
}

function CheckoutResolve({ parsed }: { parsed: CheckoutParams }) {
  const router = useRouter();
  const cart = useCart();
  const items = useMemo(() => resolveCheckoutItems(parsed, cart.data), [parsed, cart.data]);
  const cartMode = parsed.mode === "cart";
  const empty = cartMode && cart.isSuccess && items === null;

  useEffect(() => {
    if (empty) router.replace("/cart");
  }, [empty, router]);

  if (cartMode && cart.isError) return <ErrorState error={cart.error} onRetry={() => void cart.refetch()} />;
  if (!items) return <PageSkeleton />;
  return <CheckoutBody items={items} mode={parsed.mode} />;
}

function CheckoutBody({ items, mode }: { items: CheckoutItem[]; mode: CheckoutParams["mode"] }) {
  const router = useRouter();
  const qc = useQueryClient();
  const quote = useQuote(items, true);
  const createOrder = useCreateOrder();
  const [priceChanged, setPriceChanged] = useState(false);
  const form = useForm<ShippingAddressInput, unknown, ShippingAddressValues>({
    resolver: zodResolver(shippingAddressSchema),
    defaultValues: { receiverName: "", phone: "", zipCode: "", address1: "", address2: "", memo: "" },
  });

  const leave = () => (mode === "cart" ? router.replace("/cart") : router.back());

  // 화면에 보이는 견적(로딩·갱신·오류가 아닌 확정 값)만 결제할 수 있다.
  const shown = quote.data;
  const payable = shown !== undefined && !quote.isFetching && !quote.isPlaceholderData && !quote.isError;
  const shownRef = useRef({ shown, payable });
  shownRef.current = { shown, payable };

  useEffect(() => {
    const e = quote.error;
    if (e instanceof ApiError && (e.code === "SKU_NOT_ORDERABLE" || e.code === "STOCK_SOLD_OUT")) {
      toast({ variant: "destructive", title: resolveError(e).message });
      leave();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 오류가 바뀔 때만
  }, [quote.error]);

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      const { shown: q } = shownRef.current;
      if (!q) throw new Error("견적이 없습니다.");
      setPriceChanged(false);
      const res = await createOrder(
        {
          items,
          expectedTotalAmount: q.totalAmount,
          pointsToUse: 0,
          shippingAddress: { ...values, memo: values.memo ?? null },
        },
        key,
      );
      router.replace(res.orderStatus === "PAID" ? `/my/orders/${res.orderId}/complete` : `/my/orders/${res.orderId}/pay?auto=1`);
    },
    {
      onError: async (e) => {
        const code = e instanceof ApiError ? e.code : null;
        if (code === "ORDER_PRICE_CHANGED") {
          setPriceChanged(true);
          await qc.invalidateQueries({ queryKey: queryKeys.checkout.quote(items) });
          return;
        }
        if (code === "STOCK_SOLD_OUT" || code === "SKU_NOT_ORDERABLE") {
          toast({ variant: "destructive", title: resolveError(e).message });
          leave();
          return;
        }
        applyFormError(form, e);
      },
    },
  );

  if (quote.isPending && !quote.isError) return <PageSkeleton />;
  if (!shown) return <ErrorState error={quote.error} onRetry={() => void quote.refetch()} />;

  return (
    <form
      noValidate
      onSubmit={(e) => {
        // Enter 로도 제출되므로 버튼 비활성만으로는 부족하다.
        if (!shownRef.current.payable) {
          e.preventDefault();
          return;
        }
        void onSubmit(e);
      }}
      className="grid grid-cols-[minmax(0,1fr)_360px] items-start gap-10"
    >
      <fieldset disabled={submitting} className="flex min-w-0 flex-col gap-6">
        {priceChanged && (
          <div className="notice notice-warn" role="alert">
            <CircleAlert className="h-4 w-4 flex-none" aria-hidden />
            <span>
              <strong>금액이 변경되었습니다.</strong> 확인 후 다시 결제해 주세요.
            </span>
          </div>
        )}
        {form.formState.errors.root?.server && <FormNotice message={form.formState.errors.root.server.message} />}
        {quote.isError && (
          <p role="alert" className="text-[13px] text-danger">
            {resolveError(quote.error).message || "금액을 계산하지 못했습니다."}{" "}
            <button type="button" className="underline underline-offset-2" onClick={() => void quote.refetch()}>
              다시 시도
            </button>
          </p>
        )}
        <ShippingAddressForm form={form} />
        <OrderItems quote={shown} />
      </fieldset>
      <aside aria-label="결제 금액" className="sticky top-24">
        <CheckoutSummary quote={shown} payable={payable} submitting={submitting} />
      </aside>
    </form>
  );
}
