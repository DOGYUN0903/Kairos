"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { ImageUploader, type UploadedImage } from "@/components/common/ImageUploader";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { applyFormError } from "@/features/auth/apply-form-error";
import { FormNotice } from "@/features/auth/components/FormNotice";
import { useCreateBrand, useUpdateBrand, type BrandDetail } from "@/features/seller/brands/api";
import { brandSchema, type BrandValues } from "@/features/seller/brands/schemas";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

export type BrandDialogProps = { open: boolean; onOpenChange: (open: boolean) => void } & (
  | { mode: "create"; brand?: undefined }
  | { mode: "edit"; brand: BrandDetail }
);

/** 닫을 때 내용이 언마운트되므로(Radix) 폼은 열릴 때마다 새로 시작한다. */
export function BrandDialog(props: BrandDialogProps) {
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{props.mode === "create" ? "브랜드 신청" : "브랜드 수정 후 다시 신청"}</DialogTitle>
          <DialogDescription>승인된 브랜드로만 상품을 등록할 수 있습니다. 관리자 심사 후 승인됩니다.</DialogDescription>
        </DialogHeader>
        <BrandForm {...props} />
      </DialogContent>
    </Dialog>
  );
}

function BrandForm({ mode, brand, onOpenChange }: BrandDialogProps) {
  const qc = useQueryClient();
  const setBanner = useSellerBanner();
  const create = useCreateBrand();
  const update = useUpdateBrand(brand?.brandId ?? 0);
  const form = useForm<BrandValues>({
    resolver: zodResolver(brandSchema),
    defaultValues: {
      name: brand?.name ?? "",
      englishName: brand?.englishName ?? "",
      // 기존 로고를 그대로 두면 같은 fileId 로 재신청해 로고가 유지된다(바꾸거나 삭제해야 달라진다).
      logoFileId: brand?.logoFileId ?? null,
      description: brand?.description ?? "",
    },
  });
  const { register, formState, control } = form;
  const { errors } = formState;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      if (mode === "create") await create.mutateAsync({ values, key });
      else await update.mutateAsync({ values });
      // 같은 값으로 다시 신청하면 새 Idempotency-Key 가 필요하므로 성공 뒤 폼을 비운다.
      form.reset();
      toast({ title: mode === "create" ? "브랜드를 신청했습니다." : "브랜드를 다시 신청했습니다." });
      onOpenChange(false);
    },
    {
      onError: async (e) => {
        if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
          setBanner("판매자 상태 때문에 처리할 수 없습니다. 정지 중에는 브랜드를 신청할 수 없습니다.");
          await qc.invalidateQueries({ queryKey: queryKeys.seller.me });
          onOpenChange(false);
          return;
        }
        applyFormError(form, e);
      },
    },
  );

  return (
    <form onSubmit={onSubmit} noValidate>
      <fieldset disabled={submitting} className="flex flex-col gap-4">
        {errors.root?.server && <FormNotice message={errors.root.server.message} />}
        <div className="field">
          <label htmlFor="b-name">브랜드명</label>
          <input
            id="b-name"
            className="input"
            maxLength={60}
            aria-invalid={errors.name ? true : undefined}
            aria-describedby={errors.name ? "b-name-err" : undefined}
            {...register("name")}
          />
          {errors.name && (
            <span id="b-name-err" className="field-error">
              {errors.name.message}
            </span>
          )}
        </div>
        <div className="field">
          <label htmlFor="b-en">영문명 (선택)</label>
          <input id="b-en" className="input" aria-invalid={errors.englishName ? true : undefined} {...register("englishName")} />
          {errors.englishName && <span className="field-error">{errors.englishName.message}</span>}
        </div>
        <div className="field">
          <span className="text-[13px] font-medium">로고 (선택)</span>
          <Controller
            control={control}
            name="logoFileId"
            render={({ field }) => (
              <LogoField fileId={field.value} initialUrl={brand?.logoUrl ?? null} onChange={(id) => field.onChange(id)} />
            )}
          />
        </div>
        <div className="field">
          <label htmlFor="b-desc">브랜드 소개 (선택)</label>
          <textarea
            id="b-desc"
            rows={4}
            className="input py-2"
            aria-invalid={errors.description ? true : undefined}
            {...register("description")}
          />
          {errors.description && <span className="field-error">{errors.description.message}</span>}
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" onClick={() => onOpenChange(false)}>
            취소
          </button>
          <button type="submit" className="btn btn-primary">
            {submitting ? "접수 중…" : mode === "create" ? "신청하기" : "다시 신청"}
          </button>
        </div>
      </fieldset>
    </form>
  );
}


/** 폼 값은 fileId 뿐이라, 미리보기용 url 은 업로드 결과와 함께 여기서 들고 있다. */
function LogoField({ fileId, initialUrl, onChange }: { fileId: number | null; initialUrl: string | null; onChange: (id: number | null) => void }) {
  const [url, setUrl] = useState<string | null>(initialUrl);
  const value: UploadedImage[] = fileId !== null && url !== null ? [{ fileId, url }] : [];
  return (
    <ImageUploader
      label="로고 올리기"
      max={1}
      value={value}
      onChange={(next) => {
        setUrl(next[0]?.url ?? null);
        onChange(next[0]?.fileId ?? null);
      }}
    />
  );
}
