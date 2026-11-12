"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { CircleAlert } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { afterOrderHref, canPay, QuoteNotReadyError, shouldRedirectToCart, unavailableDestination } from "@/features/checkout/checkout-guard";
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

/** 만들어진 주문과 이동할 곳. 이동이 실패해도 잠긴 화면에서 다음 단계로 갈 수 있게 들고 있는다. */
type CreatedOrder = { orderId: number; href: string; paid: boolean };

function CheckoutResolve({ parsed }: { parsed: CheckoutParams }) {
  const router = useRouter();
  const cart = useCart();
  const resolved = useMemo(() => resolveCheckoutItems(parsed, cart.data), [parsed, cart.data]);
  const cartMode = parsed.mode === "cart";
  // 주문이 만들어지면 잠그고, 그 뒤 장바구니 재조회로 항목·화면이 바뀌거나 /cart 로 튕기지 않게 마지막 항목을 고정한다.
  const [created, setCreated] = useState<CreatedOrder | null>(null);
  const orderCreated = created !== null;
  const lastItems = useRef<CheckoutItem[] | null>(null);
  if (resolved && !orderCreated) lastItems.current = resolved;
  const items = orderCreated ? lastItems.current : resolved;
  const empty = cartMode && cart.isSuccess && resolved === null;

  useEffect(() => {
    if (shouldRedirectToCart({ empty, orderCreated })) router.replace("/cart");
  }, [empty, orderCreated, router]);

  if (!orderCreated && cartMode && cart.isError) return <ErrorState error={cart.error} onRetry={() => void cart.refetch()} />;
  if (!items) return <PageSkeleton />;
  return <CheckoutBody items={items} mode={parsed.mode} created={created} onOrderCreated={setCreated} />;
}

function CheckoutBody({ items, mode, created, onOrderCreated }: { items: CheckoutItem[]; mode: CheckoutParams["mode"]; created: CreatedOrder | null; onOrderCreated: (order: CreatedOrder) => void }) {
  const router = useRouter();
  const qc = useQueryClient();
  const orderCreated = created !== null;
  // 주문이 만들어진 뒤에는 견적을 다시 읽지 않는다(마지막 재고를 방금 샀다면 품절 오류가 돌아온다). 금액은 마지막 확정 견적을 그대로 보여준다.
  const quote = useQuote(items, !orderCreated);
  const createOrder = useCreateOrder();
  const [priceChanged, setPriceChanged] = useState(false);
  const form = useForm<ShippingAddressInput, unknown, ShippingAddressValues>({
    resolver: zodResolver(shippingAddressSchema),
    defaultValues: { receiverName: "", phone: "", zipCode: "", address1: "", address2: "", memo: "" },
  });

  // 화면에 보이는 견적(로딩·갱신·오류가 아닌 확정 값)만 결제할 수 있다. 주문이 만들어진 뒤에는 다시 열리지 않는다.
  const shown = quote.data;
  const payable = canPay({
    hasQuote: shown !== undefined,
    fetching: quote.isFetching,
    placeholder: quote.isPlaceholderData,
    errored: quote.isError,
    orderCreated,
  });
  const shownRef = useRef({ shown, payable });
  shownRef.current = { shown, payable };

  // 항상 정해진 위치로 보낸다(history 에 기대면 새 탭·직접 진입에서 앱을 떠난다).
  const leave = () => router.replace(unavailableDestination(mode, shownRef.current.shown?.items[0]?.productId));
  // state 가 반영되기 전의 연타·Enter 도 막도록 ref 로도 잠근다.
  const createdRef = useRef(false);
  if (orderCreated) createdRef.current = true;

  useEffect(() => {
    // 주문이 만들어진 뒤의 견적 오류가 결제 페이지 이동을 덮지 않게 한다.
    if (orderCreated || createdRef.current) return;
    const e = quote.error;
    if (e instanceof ApiError && (e.code === "SKU_NOT_ORDERABLE" || e.code === "STOCK_SOLD_OUT")) {
      toast({ variant: "destructive", title: resolveError(e).message });
      leave();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 오류가 바뀔 때만
  }, [quote.error, orderCreated]);

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      // 이미 주문이 만들어졌으면 보낼 것이 없다(성공으로 끝낸다).
      if (createdRef.current) return;
      // 보낼 순간에 확정된 견적이 아니면(재조회 중·오류) 보내지 않되, 조용히 return 하지 않고 던진다:
      // return 은 성공으로 보여 직전 시도(응답 없음)의 키가 버려지고 다음 제출이 두 번째 주문을 만든다.
      const { shown: q, payable: ok } = shownRef.current;
      if (!q || !ok) throw new QuoteNotReadyError();
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
      // 이동이 끝나기 전에 다시 눌러도 두 번째 주문이 만들어지지 않도록 영구히 잠근다.
      createdRef.current = true;
      const href = afterOrderHref(res.orderId, res.orderStatus);
      onOrderCreated({ orderId: res.orderId, href, paid: res.orderStatus === "PAID" });
      router.replace(href);
    },
    {
      onError: async (e) => {
        // 요청을 보내지 않았다. 버튼이 다시 켜지면 같은 키로 다시 제출된다.
        if (e instanceof QuoteNotReadyError) return;
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
        if (!shownRef.current.payable || createdRef.current) {
          e.preventDefault();
          return;
        }
        void onSubmit(e);
      }}
      className="grid grid-cols-[minmax(0,1fr)_360px] items-start gap-10"
    >
      <fieldset disabled={submitting || orderCreated} className="flex min-w-0 flex-col gap-6">
        {created && (
          // 이동이 실패해도(네트워크·청크 로드 오류) 잠긴 주문서에서 다음 단계로 갈 수 있어야 한다. fieldset 은 링크를 막지 않는다.
          <div className="notice" role="status">
            <span>
              주문이 접수되었습니다. 화면이 넘어가지 않으면{" "}
              <Link className="font-medium text-navy underline underline-offset-2" href={created.href}>
                {created.paid ? "주문 완료 보기" : "결제 페이지로 이동"}
              </Link>
              를 눌러 주세요.
            </span>
          </div>
        )}
        {priceChanged && (
          <div className="notice notice-warn" role="alert">
            <CircleAlert className="h-4 w-4 flex-none" aria-hidden />
            <span>
              <strong>금액이 변경되었습니다.</strong> 확인 후 다시 결제해 주세요.
            </span>
          </div>
        )}
        {form.formState.errors.root?.server && <FormNotice message={form.formState.errors.root.server.message} />}
        {quote.isError && !orderCreated && (
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
        <CheckoutSummary quote={shown} payable={payable} submitting={submitting || orderCreated} />
      </aside>
    </form>
  );
}
