"use client";

import { Camera, X } from "lucide-react";
import { useId, useRef, useState, type ChangeEvent } from "react";
import { ACCEPTED_IMAGE_TYPES, validateImageFile } from "@/components/common/ImageUploader";
import { uploadImage } from "@/features/files/api";
import { canAddImage } from "@/features/reviews/image-rules";
import { REVIEW_IMAGE_MAX } from "@/features/reviews/schemas";
import type { ReviewImage } from "@/features/reviews/api";

const TYPE_MESSAGE = "jpg, png, webp만 올릴 수 있습니다";
const SIZE_MESSAGE = "5MB 이하만 올릴 수 있습니다";

/** 선택 즉시 거른다(검사 규칙은 ImageUploader 와 같고, 후기 창의 안내 문구만 다르다). */
function rejectionMessage(file: File): string | null {
  if (validateImageFile(file) === null) return null;
  return (ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type) ? SIZE_MESSAGE : TYPE_MESSAGE;
}

/** 후기 사진 선택·업로드·미리보기. 올리는 동안 onBusyChange(true)로 알려 제출을 막게 한다. */
export function ReviewImagePicker({ value, onChange, onBusyChange, disabled }: { value: ReviewImage[]; onChange: (next: ReviewImage[]) => void; onBusyChange: (busy: boolean) => void; disabled?: boolean }) {
  const inputId = useId();
  const [uploading, setUploading] = useState(0);
  const [errors, setErrors] = useState<string[]>([]);
  const latest = useRef(value);
  latest.current = value;
  const pending = useRef(0);

  const full = !canAddImage(value.length + uploading);

  async function onPick(e: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (picked.length === 0) return;

    const nextErrors: string[] = [];
    const accepted: File[] = [];
    let room = REVIEW_IMAGE_MAX - latest.current.length - pending.current;
    for (const f of picked) {
      const bad = rejectionMessage(f);
      if (bad) nextErrors.push(bad);
      else if (room > 0) {
        accepted.push(f);
        room -= 1;
      } else nextErrors.push(`사진은 최대 ${REVIEW_IMAGE_MAX}장까지 올릴 수 있습니다`);
    }
    setErrors(nextErrors);
    if (accepted.length === 0) return;

    pending.current += accepted.length;
    setUploading(pending.current);
    onBusyChange(true);
    for (const f of accepted) {
      try {
        const r = await uploadImage(f);
        const next = [...latest.current, { fileId: r.fileId, url: r.url }];
        latest.current = next;
        onChange(next);
      } catch {
        setErrors((prev) => [...prev, "사진을 올리지 못했습니다. 다시 시도해 주세요"]);
      } finally {
        pending.current -= 1;
        setUploading(pending.current);
        if (pending.current === 0) onBusyChange(false);
      }
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {value.map((img, i) => (
          <div key={img.fileId} className="relative h-24 w-24 overflow-hidden rounded border border-border bg-surface-2">
            {/* eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로 */}
            <img src={img.url} alt={`후기 사진 ${i + 1}`} className="h-full w-full object-cover" />
            <button
              type="button"
              aria-label="사진 삭제"
              disabled={disabled}
              onClick={() => onChange(value.filter((v) => v.fileId !== img.fileId))}
              className="absolute right-1 top-1 grid h-[22px] w-[22px] place-items-center rounded-full bg-navy/75 text-white"
            >
              <X className="h-3 w-3" aria-hidden />
            </button>
          </div>
        ))}
        {Array.from({ length: uploading }, (_, i) => (
          <div key={`up-${i}`} role="status" aria-busy="true" className="grid h-24 w-24 place-items-center rounded border border-border bg-surface text-[11px] text-text-3">
            올리는 중…
          </div>
        ))}
        {!full && (
          <label
            htmlFor={inputId}
            className="flex h-24 w-24 cursor-pointer flex-col items-center justify-center gap-0.5 rounded border border-dashed border-border bg-white text-[11px] text-text-3 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-navy"
          >
            <Camera className="h-5 w-5" aria-hidden />
            <span>사진 추가</span>
            <input id={inputId} type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={disabled} className="sr-only" onChange={(e) => void onPick(e)} />
          </label>
        )}
      </div>
      {uploading > 0 ? (
        <span className="hint" role="status">
          사진을 올리는 중입니다
        </span>
      ) : (
        <span className="hint">jpg, png, webp · 5MB 이하</span>
      )}
      {errors.length > 0 && (
        <ul role="alert" className="flex flex-col gap-0.5 text-xs text-danger">
          {errors.map((m, i) => (
            <li key={`${i}-${m}`}>{m}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
