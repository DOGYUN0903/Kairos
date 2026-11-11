import { CircleAlert } from "lucide-react";
import { StatusChip } from "@/components/common/StatusChip";
import type { BrandDetail } from "@/features/seller/brands/api";

export function BrandCard({ brand, onEdit }: { brand: BrandDetail; onEdit: () => void }) {
  return (
    <li className="card flex flex-col gap-3 p-4">
      <div className="flex items-start gap-3">
        {brand.logoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
          <img src={brand.logoUrl} alt={`${brand.name} 로고`} className="h-14 w-14 flex-none rounded border border-border object-cover" />
        ) : (
          <div aria-hidden className="grid h-14 w-14 flex-none place-items-center rounded bg-surface-2 text-[10px] text-text-3">
            로고 없음
          </div>
        )}
        <div className="min-w-0 flex-1">
          <h3 className="truncate font-bold">{brand.name}</h3>
          {brand.englishName && <p className="truncate text-xs text-text-2">{brand.englishName}</p>}
          <div className="mt-1.5">
            <StatusChip kind="brand" status={brand.status} />
          </div>
        </div>
      </div>
      {brand.description && <p className="line-clamp-2 text-[13px] text-text-2">{brand.description}</p>}
      {brand.status === "REJECTED" && (
        <div className="notice notice-warn" role="alert">
          <CircleAlert className="h-4 w-4 flex-none" aria-hidden />
          <span>
            <strong>반려 사유:</strong> {brand.rejectReason ?? "사유가 등록되지 않았습니다."}
          </span>
        </div>
      )}
      {brand.status === "REJECTED" && (
        <button type="button" className="btn btn-sm self-start" onClick={onEdit}>
          수정
        </button>
      )}
    </li>
  );
}
