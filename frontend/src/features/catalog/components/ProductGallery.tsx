"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";

/** 대표 이미지 + 썸네일 버튼(키보드로 선택). */
export function ProductGallery({ imageUrls, name }: { imageUrls: string[]; name: string }) {
  const [index, setIndex] = useState(0);
  const current = imageUrls[Math.min(index, imageUrls.length - 1)];

  return (
    <section aria-label="상품 이미지">
      <div className="aspect-square overflow-hidden rounded bg-surface-2">
        {current ? (
          // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
          <img src={current} alt={`${name} 이미지 ${index + 1}/${imageUrls.length}`} className="h-full w-full object-cover" />
        ) : (
          <div className="grid h-full w-full place-items-center text-text-3">이미지 없음</div>
        )}
      </div>
      {imageUrls.length > 1 && (
        <div role="group" aria-label="이미지 선택" className="mt-3 grid grid-cols-6 gap-2">
          {imageUrls.map((url, i) => (
            <button
              key={`${url}-${i}`}
              type="button"
              aria-current={i === index ? "true" : undefined}
              aria-label={`이미지 ${i + 1}`}
              onClick={() => setIndex(i)}
              className={cn("aspect-square overflow-hidden rounded border border-border bg-surface-2 p-0", i === index && "border-2 border-navy")}
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로 */}
              <img src={url} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
