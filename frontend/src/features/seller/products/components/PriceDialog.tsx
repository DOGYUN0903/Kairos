"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo } from "react";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { Money } from "@/components/common/Money";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useChangePrice, useSellerProduct, type SellerProductSummary } from "@/features/seller/products/api";
import { priceSchema, type PriceFormInput, type PriceFormValues } from "@/features/seller/products/schemas";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { discountRate, parseWon } from "@/lib/money";

export function PriceDialog({ product, onClose }: { product: Pick<SellerProductSummary, "productId" | "name">; onClose: () => void }) {
  // 목록 요약에는 정가가 없어 상세를 불러온다.
  const detail = useSellerProduct(product.productId);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>판매가 수정</DialogTitle>
          <DialogDescription>{product.name} · 판매가는 바로 반영되며, 정가 이하여야 합니다.</DialogDescription>
        </DialogHeader>
        {detail.isPending ? (
          <Skeleton className="h-28 w-full" aria-label="불러오는 중" />
        ) : detail.isError ? (
          <p role="alert" className="text-sm text-danger">
            정가를 불러오지 못했습니다.{" "}
            <button type="button" className="underline" onClick={() => void detail.refetch()}>
              다시 시도
            </button>
          </p>
        ) : (
          <PriceForm productId={product.productId} originalPrice={detail.data.originalPrice} salePrice={detail.data.salePrice} onClose={onClose} />
        )}
      </DialogContent>
    </Dialog>
  );
}

function PriceForm({ productId, originalPrice, salePrice, onClose }: { productId: number; originalPrice: number; salePrice: number; onClose: () => void }) {
  const schema = useMemo(() => priceSchema(originalPrice), [originalPrice]);
  const form = useForm<PriceFormInput, unknown, PriceFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { salePrice: String(salePrice) },
  });
  const change = useChangePrice(productId);
  const { errors } = form.formState;
  const typed = parseWon(form.watch("salePrice") ?? "");
  const rate = typed !== null && typed >= 1 && typed <= originalPrice ? discountRate(originalPrice, typed) : null;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values) => {
      await change.mutateAsync({ salePrice: values.salePrice });
      onClose();
    },
    {
      onError: (e) => {
        if (e instanceof ApiError && e.code === "PRODUCT_INVALID_PRICE") {
          form.setError("salePrice", { type: "server", message: "판매가는 1원 이상, 정가 이하여야 합니다." }, { shouldFocus: true });
        }
      },
    },
  );

  return (
    <form onSubmit={onSubmit} noValidate>
      <fieldset disabled={submitting} className="flex flex-col gap-4">
        <div className="flex justify-between text-sm">
          <span className="text-text-2">정가</span>
          <Money value={originalPrice} className="font-medium" />
        </div>
        <div className="field">
          <label htmlFor="price-sale">판매가 (원)</label>
          <input
            id="price-sale"
            className="input"
            inputMode="numeric"
            autoComplete="off"
            aria-invalid={errors.salePrice ? true : undefined}
            aria-describedby="price-hint"
            {...form.register("salePrice")}
          />
          {errors.salePrice && <span className="field-error">{errors.salePrice.message}</span>}
          <span id="price-hint" className="hint" aria-live="polite">
            할인율 {rate === null ? "–" : `${rate}%`}
          </span>
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            취소
          </button>
          <button type="submit" className="btn btn-primary">
            {submitting ? "저장 중…" : "저장"}
          </button>
        </div>
      </fieldset>
    </form>
  );
}
