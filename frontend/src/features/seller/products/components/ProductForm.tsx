"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { ImageUploader } from "@/components/common/ImageUploader";
import { applyFormError } from "@/features/auth/apply-form-error";
import { FormNotice } from "@/features/auth/components/FormNotice";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { useSellerCapabilities } from "@/features/seller/gate/useSellerCapabilities";
import { useRegisterProduct } from "@/features/seller/products/api";
import { BrandSelect } from "@/features/seller/products/components/BrandSelect";
import { CategorySelect } from "@/features/seller/products/components/CategorySelect";
import { OptionAxesEditor } from "@/features/seller/products/components/OptionAxesEditor";
import { SkuPreviewTable } from "@/features/seller/products/components/SkuPreviewTable";
import {
  DESCRIPTION_MAX,
  IMAGES_MAX,
  NAME_MAX,
  productSchema,
  toRegisterRequest,
  type ProductFormInput,
  type ProductFormValues,
} from "@/features/seller/products/schemas";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { discountRate, formatKrw, parseWon } from "@/lib/money";
import { queryKeys } from "@/lib/query-keys";

const EMPTY: ProductFormInput = {
  brandId: Number.NaN,
  categoryId: Number.NaN,
  name: "",
  description: "",
  images: [],
  originalPrice: "",
  salePrice: "",
  options: [],
};

const Req = () => (
  <span className="text-danger" aria-hidden>
    *
  </span>
);

/** 상품 등록 폼. 수정(변경 요청)은 Task 12 에서 mode 를 넓힌다. */
export function ProductForm({ mode }: { mode: "create" }) {
  const router = useRouter();
  const qc = useQueryClient();
  const setBanner = useSellerBanner();
  const { canManage } = useSellerCapabilities();
  const register_ = useRegisterProduct();
  const form = useForm<ProductFormInput, unknown, ProductFormValues>({ resolver: zodResolver(productSchema), defaultValues: EMPTY });
  const { register, control, formState, watch } = form;
  const { errors } = formState;

  const { onSubmit, submitting } = useIdempotentSubmit(form, async (values, key) => {
    let productId: number;
    try {
      ({ productId } = await register_.mutateAsync({ body: toRegisterRequest(values), key }));
    } catch (e) {
      if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
        setBanner("판매자 상태 때문에 처리할 수 없습니다. 정지 중에는 상품을 등록할 수 없습니다.");
        await qc.invalidateQueries({ queryKey: queryKeys.seller.me });
        return;
      }
      applyFormError(form, e);
      return;
    }
    // 같은 값으로 다시 등록하면 새 Idempotency-Key 가 필요하므로 성공 뒤 폼을 비운다.
    form.reset(EMPTY);
    toast({ title: "검수 대기 상태로 등록됐습니다" });
    router.push(`/seller/products/${productId}`);
  });

  const original = parseWon(watch("originalPrice") ?? "");
  const sale = parseWon(watch("salePrice") ?? "");
  const rate = original !== null && sale !== null && original >= 1 && sale >= 1 && sale <= original ? discountRate(original, sale) : null;
  const options = watch("options") ?? [];

  return (
    <form onSubmit={onSubmit} noValidate aria-label={mode === "create" ? "상품 등록" : "상품 수정"}>
      <fieldset disabled={submitting || !canManage} className="flex flex-col gap-6">
        <div className="notice">
          <div>
            등록한 상품은 <strong>검수 대기</strong> 상태가 되며, 관리자가 승인하면 판매가 시작됩니다. 반려되면 사유를 확인하고 내용을 고쳐 다시 제출할 수
            있습니다.
          </div>
        </div>
        {!canManage && <div className="notice notice-warn">정지 중에는 상품을 등록할 수 없습니다.</div>}
        {errors.root?.server && <FormNotice message={errors.root.server.message} />}

        <section className="card" aria-labelledby="basic-h">
          <h2 id="basic-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
            기본 정보
          </h2>
          <div className="grid grid-cols-2 gap-4 p-5">
            <BrandSelect error={errors.brandId?.message} {...register("brandId", { valueAsNumber: true })} />
            <Controller
              control={control}
              name="categoryId"
              render={({ field }) => (
                <CategorySelect value={field.value} onChange={field.onChange} onBlur={field.onBlur} error={errors.categoryId?.message} />
              )}
            />
            <div className="field col-span-2">
              <label htmlFor="p-name">
                상품명
                <Req />
              </label>
              <input
                id="p-name"
                className="input"
                maxLength={NAME_MAX}
                aria-invalid={errors.name ? true : undefined}
                aria-describedby={errors.name ? "p-name-err" : "p-name-hint"}
                {...register("name")}
              />
              {errors.name ? (
                <span id="p-name-err" className="field-error">
                  {errors.name.message}
                </span>
              ) : (
                <span id="p-name-hint" className="hint">
                  최대 {NAME_MAX}자
                </span>
              )}
            </div>
            <div className="field col-span-2">
              <label htmlFor="p-desc">
                상품 설명
                <Req />
              </label>
              <textarea
                id="p-desc"
                rows={5}
                className="input py-2"
                maxLength={DESCRIPTION_MAX}
                aria-invalid={errors.description ? true : undefined}
                aria-describedby={errors.description ? "p-desc-err" : undefined}
                {...register("description")}
              />
              {errors.description && (
                <span id="p-desc-err" className="field-error">
                  {errors.description.message}
                </span>
              )}
            </div>
          </div>
        </section>

        <section className="card" aria-labelledby="img-h">
          <h2 id="img-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
            상품 이미지
            <Req />
          </h2>
          <div className="flex flex-col gap-2 p-5">
            <Controller
              control={control}
              name="images"
              render={({ field }) => <ImageUploader label="이미지 추가" max={IMAGES_MAX} value={field.value} onChange={field.onChange} />}
            />
            {errors.images && <span className="field-error">{errors.images.message ?? errors.images.root?.message}</span>}
            <p className="hint">첫 번째 이미지가 대표 이미지로 목록에 노출됩니다. 화살표 버튼으로 순서를 바꿀 수 있습니다.</p>
          </div>
        </section>

        <section className="card" aria-labelledby="price-h">
          <h2 id="price-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
            가격
          </h2>
          <div className="grid grid-cols-2 gap-4 p-5">
            {(["originalPrice", "salePrice"] as const).map((name) => (
              <div key={name} className="field">
                <label htmlFor={`p-${name}`}>
                  {name === "originalPrice" ? "정가" : "판매가"}
                  <Req />
                </label>
                <div className="flex items-center gap-2">
                  <input
                    id={`p-${name}`}
                    className="input flex-1 text-right tabular-nums"
                    inputMode="numeric"
                    autoComplete="off"
                    placeholder="0"
                    aria-invalid={errors[name] ? true : undefined}
                    aria-describedby={errors[name] ? `p-${name}-err` : undefined}
                    {...register(name)}
                  />
                  <span className="text-sm text-text-2">원</span>
                </div>
                {errors[name] && (
                  <span id={`p-${name}-err`} className="field-error">
                    {errors[name]?.message}
                  </span>
                )}
              </div>
            ))}
            <p className="col-span-2 text-sm" aria-live="polite">
              <span className="text-text-2">할인율 </span>
              <strong className="text-danger">{rate === null ? "–" : `${rate}%`}</strong>
              <span className="hint">
                {" "}
                {rate !== null && original !== null && sale !== null
                  ? `· ${formatKrw(original)}에서 ${formatKrw(sale)}. `
                  : "· "}
                판매가는 정가 이하여야 하며, 둘 다 0보다 커야 합니다.
              </span>
            </p>
          </div>
        </section>

        <section className="card" aria-labelledby="opt-h">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <h2 id="opt-h" className="text-[15px] font-bold">
              옵션
            </h2>
            <span className="text-xs text-text-2">옵션 축은 최대 2개 ({options.length} / 2)</span>
          </div>
          <div className="p-5">
            <Controller
              control={control}
              name="options"
              render={({ field }) => <OptionAxesEditor value={field.value} onChange={field.onChange} errors={errors.options} />}
            />
          </div>
        </section>

        <SkuPreviewTable options={options} />

        <div className="notice notice-warn">
          <div>
            등록 후 <strong>상품명, 설명, 이미지, 카테고리, 옵션 구성</strong>을 바꾸면 변경 요청으로 저장되어 재검수를 받습니다. 검수 중에는 기존 내용으로 계속
            판매됩니다.
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <Link className="btn btn-ghost" href="/seller/products">
            취소
          </Link>
          <button type="submit" className="btn btn-primary">
            {submitting ? "등록 중…" : "검수 요청"}
          </button>
        </div>
      </fieldset>
    </form>
  );
}
