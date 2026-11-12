"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { CircleAlert } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { afterOrderHref, canPay, orderErrorAction, QuoteNotReadyError, shouldRedirectToCart, submitSnapshot, unavailableDestination } from "@/features/checkout/checkout-guard";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { FormNotice } from "@/features/auth/components/FormNotice";
import { applyFormError } from "@/features/auth/apply-form-error";
import { useCart } from "@/features/cart/api";
import { useCreateOrder, useQuote, type QuoteLine, type QuoteResult } from "@/features/checkout/api";
import { clearCoupons, couponAppliedElsewhere, EMPTY_SELECTION, isSameSelection, selectCartCoupon, selectProductCoupon, setPoints, type BenefitSelection } from "@/features/checkout/benefit-selection";
import { BenefitSection } from "@/features/checkout/components/BenefitSection";
import { CouponPickerDialog } from "@/features/checkout/components/CouponPickerDialog";
import { validatePoints } from "@/features/checkout/points-input";
import { recoverSelection } from "@/features/checkout/quote-recovery";
import { usePointSummary } from "@/features/points/api";
import { Money } from "@/components/common/Money";
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

type Picker = { kind: "product"; skuId: number } | { kind: "cart" };

function CheckoutBody({ items, mode, created, onOrderCreated }: { items: CheckoutItem[]; mode: CheckoutParams["mode"]; created: CreatedOrder | null; onOrderCreated: (order: CreatedOrder) => void }) {
  const router = useRouter();
  const qc = useQueryClient();
  const orderCreated = created !== null;
  // 상태는 선택(쿠폰·포인트)과 그 선택으로 받은 견적 두 가지다. 화면의 모든 금액은 견적 값이다.
  const [selection, setSelection] = useState<BenefitSelection>(EMPTY_SELECTION);
  const [pointsRaw, setPointsRaw] = useState("");
  const [picker, setPicker] = useState<Picker | null>(null);
  // 주문이 만들어진 뒤에는 견적을 다시 읽지 않는다(마지막 재고를 방금 샀다면 품절 오류가 돌아온다). 금액은 마지막 확정 견적을 그대로 보여준다.
  const quote = useQuote(items, selection, !orderCreated);
  const createOrder = useCreateOrder();
  const points = usePointSummary();
  const [priceChanged, setPriceChanged] = useState(false);
  const form = useForm<ShippingAddressInput, unknown, ShippingAddressValues>({
    resolver: zodResolver(shippingAddressSchema),
    defaultValues: { receiverName: "", phone: "", zipCode: "", address1: "", address2: "", memo: "" },
  });

  // 마지막으로 확정(placeholder 도, 오류도 아닌)된 견적과 그 견적을 만든 선택. 쿠폰 오류로 되돌릴 곳이고, 복구 중 화면에 남겨 둘 견적이다.
  const lastGoodRef = useRef<QuoteResult | null>(null);
  if (quote.isSuccess && !quote.isPlaceholderData && quote.data) lastGoodRef.current = quote.data;
  const shownResult: QuoteResult | undefined = quote.data ?? lastGoodRef.current ?? undefined;
  const shown = shownResult?.quote;

  // 포인트 입력 칸이 검증을 통과했고 그 값이 선택(=견적 요청)에 반영됐을 때만 결제할 수 있다.
  const pointsCheck = validatePoints(pointsRaw, shown?.maxUsablePoints ?? 0);
  const pointsSettled = pointsCheck.ok && pointsCheck.points === selection.points;

  // 화면에 보이는 견적(로딩·갱신·오류가 아닌 확정 값)만 결제할 수 있다. 주문이 만들어진 뒤에는 다시 열리지 않는다.
  const readyToPay = canPay({
    hasQuote: quote.data !== undefined,
    fetching: quote.isFetching,
    placeholder: quote.isPlaceholderData,
    errored: quote.isError,
    orderCreated,
    pointsSettled,
  });
  // 제출할 금액과 선택은 같은 견적에서 같은 렌더에 꺼낸다(quote.data 는 그 견적을 만든 선택을 함께 들고 있다).
  const snapshot = submitSnapshot({ quote: quote.data?.quote, quoteSelection: quote.data?.selection ?? null, selection, payable: readyToPay });
  const payable = snapshot !== null;
  const shownRef = useRef({ shown, snapshot });
  shownRef.current = { shown, snapshot };

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

  // 견적이 쿠폰·포인트 오류로 실패하면 선택을 되돌리고 다시 견적한다. 같은 오류 객체로는 한 번만 시도한다(무한 반복 방지).
  const handledError = useRef<unknown>(null);
  const lastGoodSelection = lastGoodRef.current?.selection ?? EMPTY_SELECTION;
  const recovery = quote.error instanceof ApiError ? recoverSelection(quote.error.code, selection, lastGoodSelection) : null;
  useEffect(() => {
    const e = quote.error;
    if (orderCreated || createdRef.current || !e || handledError.current === e) return;
    handledError.current = e;
    if (!(e instanceof ApiError)) return;
    const r = recoverSelection(e.code, selection, lastGoodRef.current?.selection ?? EMPTY_SELECTION);
    if (!r) return; // 더 물러설 곳이 없다: 오류 상태를 보인다
    setSelection(r.next);
    setPointsRaw(r.next.points === 0 ? "" : String(r.next.points));
    toast({ title: r.notice, description: e.code.startsWith("COUPON_") ? e.message : undefined });
    // 캐시에 남은 옛 견적이 죽은 쿠폰을 쓸 수 있다고 보이지 않게 한다.
    void qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 오류가 바뀔 때만
  }, [quote.error, orderCreated]);

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      // 이미 주문이 만들어졌으면 보낼 것이 없다(성공으로 끝낸다).
      if (createdRef.current) return;
      // 보낼 순간에 확정된 견적이 아니면(재조회 중·오류·선택이 견적과 다름) 보내지 않되, 조용히 return 하지 않고 던진다:
      // return 은 성공으로 보여 직전 시도(응답 없음)의 키가 버려지고 다음 제출이 두 번째 주문을 만든다.
      const snap = shownRef.current.snapshot;
      if (!snap) throw new QuoteNotReadyError();
      setPriceChanged(false);
      const res = await createOrder(
        {
          items,
          expectedTotalAmount: snap.expectedTotalAmount,
          ...snap.benefit,
          shippingAddress: { ...values, memo: values.memo ?? null },
        },
        key,
      );
      // 이동이 끝나기 전에 다시 눌러도 두 번째 주문이 만들어지지 않도록 영구히 잠근다.
      createdRef.current = true;
      const href = afterOrderHref(res.orderId, res.orderStatus, res.totalAmount);
      onOrderCreated({ orderId: res.orderId, href, paid: res.orderStatus === "PAID" });
      router.replace(href);
    },
    {
      // 같은 키로 내용이 다른 요청이 나가면 서버가 422 IDEMPOTENCY_KEY_REUSED 로 거절한다: 금액·혜택 선택이 바뀌면 새 키.
      // 견적이 아직 없어 알 수 없으면 빈 문자열(직전 지문 유지).
      fingerprint: () => {
        const snap = shownRef.current.snapshot;
        return snap ? JSON.stringify([snap.expectedTotalAmount, snap.benefit, items]) : "";
      },
      onError: async (e) => {
        // 요청을 보내지 않았다. 버튼이 다시 켜지면 같은 키로 다시 제출된다.
        if (e instanceof QuoteNotReadyError) return;
        const code = e instanceof ApiError ? e.code : null;
        if (code === "ORDER_PRICE_CHANGED") {
          // 쿠폰·포인트 선택은 유지해 다시 견적한다(그 견적이 실패하면 복구 규칙이 비운다).
          setPriceChanged(true);
          await qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes });
          return;
        }
        if (code === "STOCK_SOLD_OUT" || code === "SKU_NOT_ORDERABLE") {
          toast({ variant: "destructive", title: resolveError(e).message });
          leave();
          return;
        }
        const action = code ? orderErrorAction(code) : null;
        if (action === "CLEAR_COUPONS") {
          setSelection((s) => clearCoupons(s));
          toast({ variant: "destructive", title: "쿠폰을 사용할 수 없습니다. 다시 선택해 주세요." });
          void qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes });
          return;
        }
        if (action === "CLEAR_POINTS") {
          setSelection((s) => setPoints(s, 0));
          setPointsRaw("");
          toast({ variant: "destructive", title: resolveError(e).message });
          void qc.invalidateQueries({ queryKey: queryKeys.points.root });
          void qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes });
          return;
        }
        applyFormError(form, e);
      },
    },
  );

  if (!shownResult || !shown) {
    if (quote.isPending) return <PageSkeleton />;
    return <ErrorState error={quote.error} onRetry={() => void quote.refetch()} />;
  }

  // 보이는 견적이 지금 선택으로 받은 것이 아니면(새 견적을 기다리는 중) 선택한 쿠폰의 할인액·포인트 한도를 믿지 않는다.
  const requoting = quote.isFetching || !isSameSelection(shownResult.selection, selection);
  const pickerLine: QuoteLine | undefined = picker?.kind === "product" ? shown.items.find((l) => l.skuId === picker.skuId) : undefined;

  return (
    <form
      noValidate
      onSubmit={(e) => {
        // Enter 로도 제출되므로 버튼 비활성만으로는 부족하다.
        if (!shownRef.current.snapshot || createdRef.current) {
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
                {created.paid ? "주문 완료 보기" : created.href.endsWith("/pay?auto=1") ? "결제 페이지로 이동" : "주문 보기"}
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
        {quote.isError && !orderCreated && recovery === null && (
          <p role="alert" className="text-[13px] text-danger">
            {resolveError(quote.error).message || "금액을 계산하지 못했습니다."}{" "}
            <button type="button" className="underline underline-offset-2" onClick={() => void quote.refetch()}>
              다시 시도
            </button>
          </p>
        )}
        <ShippingAddressForm form={form} />
        <OrderItems quote={shown} selection={selection} requoting={requoting} onPickCoupon={(l) => setPicker({ kind: "product", skuId: l.skuId })} />
        <BenefitSection
          requoting={requoting}
          quote={shown}
          selection={selection}
          balance={points.data?.balance}
          pointsRaw={pointsRaw}
          onPointsRaw={setPointsRaw}
          onCommitPoints={(p) => setSelection((s) => setPoints(s, p))}
          onPickCart={() => setPicker({ kind: "cart" })}
        />
      </fieldset>
      <aside aria-label="결제 금액" className="sticky top-24">
        <CheckoutSummary quote={shown} payable={payable} submitting={submitting || orderCreated} />
      </aside>
      {picker?.kind === "product" && pickerLine && (
        <CouponPickerDialog
          key={`p${pickerLine.skuId}`}
          title="상품 쿠폰 선택"
          scope="상품 쿠폰"
          subject={
            <div className="flex items-center gap-3 border-b border-border pb-4 text-[13px]">
              <div className="min-w-0">
                <div className="text-xs text-text-2">{pickerLine.brandName}</div>
                <div className="truncate font-medium">{pickerLine.productName}</div>
                <div className="text-text-2">
                  {pickerLine.optionDisplayName} · 수량 {pickerLine.quantity}개 · <Money value={pickerLine.lineAmount} />
                </div>
              </div>
            </div>
          }
          coupons={pickerLine.availableCoupons}
          selectedId={selection.productCoupons[pickerLine.skuId] ?? null}
          appliedElsewhere={(couponId) => couponAppliedElsewhere(selection, pickerLine.skuId, couponId)}
          onApply={(id) => setSelection((s) => selectProductCoupon(s, pickerLine.skuId, id))}
          onClose={() => setPicker(null)}
        />
      )}
      {picker?.kind === "cart" && (
        <CouponPickerDialog
          key="cart"
          title="장바구니 쿠폰 선택"
          scope="장바구니 쿠폰"
          coupons={shown.cartCoupons}
          selectedId={selection.cartCouponId}
          onApply={(id) => setSelection((s) => selectCartCoupon(s, id))}
          onClose={() => setPicker(null)}
        />
      )}
    </form>
  );
}
