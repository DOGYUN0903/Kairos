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
import {
  useCreateChangeRequest,
  useEditProductContent,
  useInvalidateProduct,
  useRegisterProduct,
  type SellerProductDetail,
} from "@/features/seller/products/api";
import { BrandSelect } from "@/features/seller/products/components/BrandSelect";
import { CategorySelect } from "@/features/seller/products/components/CategorySelect";
import { OptionAxesEditor } from "@/features/seller/products/components/OptionAxesEditor";
import { SkuImpactNotice } from "@/features/seller/products/components/SkuImpactNotice";
import { SkuPreviewTable } from "@/features/seller/products/components/SkuPreviewTable";
import {
  DESCRIPTION_MAX,
  IMAGES_MAX,
  NAME_MAX,
  productSchema,
  toChangeRequest,
  toContentRequest,
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

function fromDetail(d: SellerProductDetail): ProductFormInput {
  return {
    brandId: d.brandId,
    categoryId: d.categoryId,
    name: d.name,
    description: d.description,
    images: d.images.map((i) => ({ fileId: i.fileId, url: i.url })),
    originalPrice: String(d.originalPrice),
    salePrice: String(d.salePrice),
    // 숨은 ID 를 행에 실어 두어 이름을 바꿔도 같은 옵션·값으로 보낸다.
    options: d.options.map((o) => ({
      optionId: o.optionId,
      name: o.name,
      values: o.values.map((v) => v.name),
      valueIds: o.values.map((v) => v.valueId),
    })),
  };
}

export type ProductFormProps = (
  | { mode: "create"; initial?: undefined }
  /** 판매 중·일시 중지: 변경 요청(정가 포함, 재검수). */
  | { mode: "change-request"; initial: SellerProductDetail }
  /** 검수 대기·반려: 내용 직접 수정(가격 제외, 다시 검수 대기). */
  | { mode: "edit"; initial: SellerProductDetail }
) & {
  /** 제출 성공 뒤 상세로 떠나는 중임을 부모에 알린다(새 상세가 와도 "검수 중" 안내로 바꾸지 않도록). */
  leavingRef?: { current: boolean };
};

/** 상품 등록·변경 요청·직접 수정 폼. 브랜드·판매가는 등록 때만 입력한다(이후 판매가는 상세의 "판매가 수정"). */
export function ProductForm({ mode, initial, leavingRef }: ProductFormProps) {
  const router = useRouter();
  const qc = useQueryClient();
  const setBanner = useSellerBanner();
  const { canManage } = useSellerCapabilities();
  const register_ = useRegisterProduct();
  const productId = initial?.productId ?? 0;
  const changeRequest = useCreateChangeRequest(productId);
  const editContent = useEditProductContent(productId);
  const invalidateProduct = useInvalidateProduct(productId);
  const isCreate = mode === "create";
  const defaults = initial ? fromDetail(initial) : EMPTY;
  const form = useForm<ProductFormInput, unknown, ProductFormValues>({ resolver: zodResolver(productSchema), defaultValues: defaults });
  const { register, control, formState, watch } = form;
  const { errors } = formState;

  const { onSubmit, submitting } = useIdempotentSubmit(form, async (values, key) => {
    if (initial) {
      const detailPath = `/seller/products/${initial.productId}`;
      // 상세로 먼저 이동한 뒤 무효화한다. 수정 화면에서 먼저 새 상세를 받으면 "검수 중인 변경 요청" 안내가 잠깐 보인다.
      const leave = () => {
        if (leavingRef) leavingRef.current = true;
        router.push(detailPath);
        void invalidateProduct();
      };
      try {
        if (mode === "change-request") await changeRequest.mutateAsync({ body: toChangeRequest(values), key });
        else await editContent.mutateAsync(toContentRequest(values));
      } catch (e) {
        if (e instanceof ApiError && e.code === "CHANGE_REQUEST_ALREADY_PENDING") {
          toast({ variant: "destructive", title: "이미 검수 중인 변경 요청이 있습니다", description: "철회한 뒤 다시 요청할 수 있습니다." });
          leave();
          return;
        }
        if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
          setBanner("판매자 상태 때문에 처리할 수 없습니다.");
          await qc.invalidateQueries({ queryKey: queryKeys.seller.me });
          return;
        }
        applyFormError(form, e);
        return;
      }
      form.reset(form.getValues()); // 이후 같은 값 재제출에는 새 키가 쓰이도록 dirty 기준을 갱신
      toast({ title: mode === "change-request" ? "변경 요청이 접수됐습니다" : "검수 대기 상태로 다시 제출됐습니다" });
      leave();
      return;
    }
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
    <form onSubmit={onSubmit} noValidate aria-label={isCreate ? "상품 등록" : mode === "change-request" ? "상품 변경 요청" : "상품 수정"}>
      <fieldset disabled={submitting || !canManage} className="flex flex-col gap-6">
        {mode === "change-request" ? (
          <div className="notice">
            <div>
              상품명, 설명, 이미지, 카테고리, 옵션 구성, 정가를 바꾸면 <strong>변경 요청</strong>으로 접수되어 재검수를 받습니다. 검수 중에도 기존 내용으로 계속
              판매되며, 상품당 하나만 대기할 수 있습니다.
            </div>
          </div>
        ) : mode === "edit" ? (
          <div className="notice">
            <div>
              수정하면 다시 <strong>검수 대기</strong> 상태가 됩니다.
              {initial?.status === "REJECTED" && initial.rejectReason && <span className="mt-1 block text-danger">반려 사유: {initial.rejectReason}</span>}
            </div>
          </div>
        ) : (
        <div className="notice">
          <div>
            등록한 상품은 <strong>검수 대기</strong> 상태가 되며, 관리자가 승인하면 판매가 시작됩니다. 반려되면 사유를 확인하고 내용을 고쳐 다시 제출할 수
            있습니다.
          </div>
        </div>
        )}
        {!canManage && <div className="notice notice-warn">정지 중에는 상품을 {isCreate ? "등록" : "수정"}할 수 없습니다.</div>}
        {errors.root?.server && <FormNotice message={errors.root.server.message} />}

        <section className="card" aria-labelledby="basic-h">
          <h2 id="basic-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">
            기본 정보
          </h2>
          <div className="grid grid-cols-2 gap-4 p-5">
            {isCreate ? (
              <BrandSelect error={errors.brandId?.message} {...register("brandId", { valueAsNumber: true })} />
            ) : (
              <div className="field">
                <span className="text-[13px] font-medium">브랜드</span>
                <p className="input flex items-center bg-surface text-text-2" aria-readonly>
                  {initial?.brandName}
                </p>
                <span className="hint">브랜드는 바꿀 수 없습니다.</span>
              </div>
            )}
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
                    readOnly={!isCreate && (name === "salePrice" || mode === "edit")}
                    aria-invalid={errors[name] ? true : undefined}
                    aria-describedby={errors[name] ? `p-${name}-err` : undefined}
                    {...register(name)}
                  />
                  <span className="text-sm text-text-2">원</span>
                </div>
                {errors[name] ? (
                  <span id={`p-${name}-err`} className="field-error">
                    {errors[name]?.message}
                  </span>
                ) : !isCreate && (name === "salePrice" || mode === "edit") ? (
                  <span className="hint">{name === "salePrice" ? "판매가는 상세 화면의 \"판매가 수정\"으로 바로 바꿉니다." : "검수 중에는 가격을 바꿀 수 없습니다."}</span>
                ) : null}
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

        {/* 수정은 백엔드가 같은 규칙(값 ID 유지 → SKU 유지, 사라진 조합 → 단종)으로 반영하므로 제출 전에 영향을 보여 준다. */}
        {initial ? (
          <SkuImpactNotice initial={initial} options={options} />
        ) : (
          <SkuPreviewTable options={options} />
        )}

        {isCreate && <div className="notice notice-warn">
          <div>
            등록 후 <strong>상품명, 설명, 이미지, 카테고리, 옵션 구성</strong>을 바꾸면 변경 요청으로 저장되어 재검수를 받습니다. 검수 중에는 기존 내용으로 계속
            판매됩니다.
          </div>
        </div>}

        <div className="flex justify-end gap-2">
          <Link className="btn btn-ghost" href={initial ? `/seller/products/${initial.productId}` : "/seller/products"}>
            취소
          </Link>
          <button type="submit" className="btn btn-primary">
            {submitting ? "제출 중…" : mode === "change-request" ? "변경 요청" : mode === "edit" ? "다시 검수 요청" : "검수 요청"}
          </button>
        </div>
      </fieldset>
    </form>
  );
}
