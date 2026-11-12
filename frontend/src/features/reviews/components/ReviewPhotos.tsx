"use client";

import { X } from "lucide-react";
import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";

/** 후기 사진 썸네일 줄. 누르면 크게 본다. */
export function ReviewPhotos({ urls }: { urls: string[] }) {
  const [open, setOpen] = useState<number | null>(null);
  if (urls.length === 0) return null;
  const current = open === null ? null : urls[open];
  return (
    <>
      <div className="mt-3 flex gap-2">
        {urls.map((url, i) => (
          <button
            key={url}
            type="button"
            aria-label={`사진 ${i + 1} 크게 보기`}
            onClick={() => setOpen(i)}
            className="h-[88px] w-[88px] overflow-hidden rounded border border-border p-0"
          >
            {/* eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로 */}
            <img src={url} alt="" className="h-full w-full object-cover" />
          </button>
        ))}
      </div>
      <Dialog open={current != null} onOpenChange={(o) => !o && setOpen(null)}>
        <DialogContent aria-label="후기 사진 크게 보기" className="w-[640px] max-w-[92vw] border-0 bg-transparent p-0 shadow-none [&>button:last-child]:hidden">
          <DialogTitle className="sr-only">후기 사진 크게 보기</DialogTitle>
          <DialogDescription className="sr-only">
            {open !== null ? `${open + 1} / ${urls.length}` : ""}
          </DialogDescription>
          {current && (
            // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로
            <img src={current} alt={`후기 사진 ${(open ?? 0) + 1}`} className="aspect-square w-full rounded-lg bg-surface-2 object-contain" />
          )}
          <button
            type="button"
            aria-label="닫기"
            onClick={() => setOpen(null)}
            className="absolute right-2 top-2 grid h-8 w-8 place-items-center rounded bg-white/90 text-text-3 hover:text-navy"
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </DialogContent>
      </Dialog>
    </>
  );
}
