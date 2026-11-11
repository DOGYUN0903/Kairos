"use client";

import { ArrowDown, ArrowUp, Trash2 } from "lucide-react";
import { useId, useRef, useState, type ChangeEvent } from "react";
import { ApiError } from "@/api/client";
import { Skeleton } from "@/components/ui/skeleton";
import { useUploadImage } from "@/features/files/api";

export type UploadedImage = { fileId: number; url: string };

const ICON_BTN =
  "inline-grid h-9 w-9 place-items-center rounded-full border border-border bg-bg text-text-2 hover:border-navy hover:text-navy disabled:cursor-not-allowed disabled:opacity-40";

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;

/** 선택 즉시 하는 클라이언트 검증. 통과하면 null, 아니면 인라인 오류 문구. */
export function validateImageFile(file: File): string | null {
  if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    return `${file.name}: JPG, PNG, WEBP 형식만 올릴 수 있습니다.`;
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return `${file.name}: 파일 크기는 5MB 이하여야 합니다.`;
  }
  return null;
}

function serverMessage(file: File, e: unknown): string {
  if (e instanceof ApiError && e.code === "FILE_UNSUPPORTED_FORMAT") {
    return `${file.name}: 지원하지 않는 이미지 형식입니다.`;
  }
  if (e instanceof ApiError && e.status === 413) {
    return `${file.name}: 파일 크기는 5MB 이하여야 합니다.`;
  }
  return `${file.name}: 업로드하지 못했습니다. 다시 시도해 주세요.`;
}

/**
 * 이미지 업로더(브랜드 로고 max=1, 상품 이미지 max=10).
 * 업로드 진행률은 fetch 가 주지 않으므로 항목 스켈레톤으로 진행 중임을 보여 준다.
 */
export function ImageUploader({
  value,
  onChange,
  max,
  label,
}: {
  value: UploadedImage[];
  onChange: (next: UploadedImage[]) => void;
  max: number;
  label: string;
}) {
  const inputId = useId();
  const errId = `${inputId}-err`;
  const upload = useUploadImage();
  const [uploading, setUploading] = useState(0);
  const [errors, setErrors] = useState<string[]>([]);
  // 여러 장을 차례로 올리는 동안에도 최신 목록 위에 더하기 위해 ref 로 들고 있다.
  const latest = useRef(value);
  latest.current = value;

  const full = value.length + uploading >= max;

  async function onPick(e: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(e.target.files ?? []);
    e.target.value = ""; // 같은 파일을 다시 고를 수 있게 비운다.
    if (picked.length === 0) return;

    const nextErrors: string[] = [];
    const accepted: File[] = [];
    let room = max - latest.current.length - uploading;
    for (const f of picked) {
      const invalid = validateImageFile(f);
      if (invalid) nextErrors.push(invalid);
      else if (room > 0) {
        accepted.push(f);
        room -= 1;
      } else nextErrors.push(`${f.name}: 이미지는 최대 ${max}장까지 올릴 수 있습니다.`);
    }
    setErrors(nextErrors);

    setUploading((n) => n + accepted.length);
    for (const f of accepted) {
      try {
        const result = await upload.mutateAsync(f);
        const next = [...latest.current, { fileId: result.fileId, url: result.url }];
        latest.current = next;
        onChange(next);
      } catch (err) {
        setErrors((prev) => [...prev, serverMessage(f, err)]);
      } finally {
        setUploading((n) => n - 1);
      }
    }
  }

  function move(index: number, delta: -1 | 1) {
    const target = index + delta;
    if (target < 0 || target >= value.length) return;
    const next = [...value];
    const a = next[index];
    const b = next[target];
    if (!a || !b) return;
    next[index] = b;
    next[target] = a;
    onChange(next);
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <label
          htmlFor={inputId}
          className={`btn btn-sm ${full ? "cursor-not-allowed opacity-60" : "cursor-pointer"} has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-navy has-[:focus-visible]:ring-offset-2`}
        >
          {label}
          <input
            id={inputId}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple={max > 1}
            disabled={full}
            className="sr-only"
            aria-describedby={errors.length > 0 ? `${errId} ${inputId}-hint` : `${inputId}-hint`}
            onChange={(e) => void onPick(e)}
          />
        </label>
        <span id={`${inputId}-hint`} className="hint">
          JPG·PNG·WEBP, 5MB 이하 · {value.length}/{max}장
        </span>
      </div>

      {errors.length > 0 && (
        <ul id={errId} role="alert" className="flex flex-col gap-0.5 text-xs text-danger">
          {errors.map((m, i) => (
            <li key={`${i}-${m}`}>{m}</li>
          ))}
        </ul>
      )}

      {(value.length > 0 || uploading > 0) && (
        <ul className="flex flex-wrap gap-3" aria-label={`${label} 목록`}>
          {value.map((img, i) => (
            <li key={img.fileId} className="flex w-[104px] flex-col gap-1.5">
              <div className="relative aspect-square overflow-hidden rounded border border-border bg-surface-2">
                {/* eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용) */}
                <img src={img.url} alt={`${i + 1}번째 이미지`} className="h-full w-full object-cover" />
                {i === 0 && (
                  <span className="absolute left-1 top-1 rounded-sm bg-navy px-1.5 py-px text-[10px] font-bold text-white">대표</span>
                )}
              </div>
              <div className="flex justify-between">
                <button
                  type="button"
                  className={ICON_BTN}
                  aria-label={`${i + 1}번째 이미지 위로 이동`}
                  disabled={i === 0}
                  onClick={() => move(i, -1)}
                >
                  <ArrowUp className="h-3.5 w-3.5" aria-hidden />
                </button>
                <button
                  type="button"
                  className={ICON_BTN}
                  aria-label={`${i + 1}번째 이미지 아래로 이동`}
                  disabled={i === value.length - 1}
                  onClick={() => move(i, 1)}
                >
                  <ArrowDown className="h-3.5 w-3.5" aria-hidden />
                </button>
                <button
                  type="button"
                  className={ICON_BTN}
                  aria-label={`${i + 1}번째 이미지 삭제`}
                  onClick={() => onChange(value.filter((_, j) => j !== i))}
                >
                  <Trash2 className="h-3.5 w-3.5" aria-hidden />
                </button>
              </div>
            </li>
          ))}
          {Array.from({ length: uploading }, (_, i) => (
            <li key={`up-${i}`} className="w-[104px]" aria-busy="true" aria-label="업로드 중">
              <Skeleton className="aspect-square w-full" />
              <span className="hint">업로드 중…</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
