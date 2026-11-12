"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useCreateCategory, useUpdateCategory } from "@/features/admin/categories/api";
import { categorySchema, type CategoryValues } from "@/features/admin/categories/schemas";
import { toast } from "@/hooks/use-toast";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";

export type CategoryDialogState =
  | { mode: "create"; parentId: number | null; parentName?: string; initial: CategoryValues }
  | { mode: "edit"; id: number; initial: CategoryValues };

export function CategoryDialog({ state, onClose }: { state: CategoryDialogState | null; onClose: () => void }) {
  return (
    <Dialog open={state !== null} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>{state && <CategoryForm key={state.mode === "edit" ? state.id : `new-${state.parentId ?? "root"}`} state={state} onClose={onClose} />}</DialogContent>
    </Dialog>
  );
}

function titleOf(s: CategoryDialogState): string {
  if (s.mode === "edit") return "카테고리 수정";
  return s.parentId === null ? "대분류 추가" : `${s.parentName ?? ""} 소분류 추가`.trim();
}

function CategoryForm({ state, onClose }: { state: CategoryDialogState; onClose: () => void }) {
  const create = useCreateCategory();
  const update = useUpdateCategory();
  const form = useForm<CategoryValues>({ resolver: zodResolver(categorySchema), defaultValues: state.initial });
  const { register, formState } = form;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      if (state.mode === "create") await create.mutateAsync({ parentId: state.parentId, values, key });
      else await update.mutateAsync({ id: state.id, values });
      toast({ title: state.mode === "create" ? "카테고리를 추가했습니다." : "카테고리를 수정했습니다." });
      form.reset();
      onClose();
    },
    {
      onError: (e) => {
        const r = resolveError(e);
        if (e instanceof ApiError && e.status === 400) {
          // INVALID_REQUEST 의 서버 메시지는 "필드: 사유" 형태라 필드 이름이 있으면 그 필드에, 없으면 이름에 붙인다.
          const field = e.message.includes("displayOrder") ? "displayOrder" : "name";
          form.setError(field, { message: field === "displayOrder" ? "표시 순서는 0 이상의 정수여야 합니다." : r.message });
        } else toast({ variant: "destructive", title: r.message, ...(r.traceId ? { description: `문의 시 전달: ${r.traceId}` } : {}) });
      },
    },
  );

  return (
    <>
      <DialogHeader>
        <DialogTitle>{titleOf(state)}</DialogTitle>
        <DialogDescription>{state.mode === "edit" ? "이름과 표시 순서를 바꿉니다." : "이름과 표시 순서를 정합니다. 작은 숫자가 먼저 보입니다."}</DialogDescription>
      </DialogHeader>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="field">
          <label htmlFor="cat-name">이름</label>
          <input id="cat-name" className="input" maxLength={60} autoComplete="off" aria-invalid={formState.errors.name ? "true" : undefined} {...register("name")} />
          {formState.errors.name && <p role="alert" className="field-error">{formState.errors.name.message}</p>}
        </div>
        <div className="field">
          <label htmlFor="cat-order">표시 순서</label>
          <input id="cat-order" type="number" inputMode="numeric" step={1} min={0} className="input" aria-invalid={formState.errors.displayOrder ? "true" : undefined} {...register("displayOrder", { valueAsNumber: true })} />
          {formState.errors.displayOrder && <p role="alert" className="field-error">{formState.errors.displayOrder.message}</p>}
        </div>
        <DialogFooter>
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={submitting}>취소</button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? "저장 중…" : "저장"}</button>
        </DialogFooter>
      </form>
    </>
  );
}
