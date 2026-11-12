import { formatDate } from "@/lib/datetime";

/** 판매자 답글 상자(들여쓴 상자, 시안 .review__reply). */
export function ReviewReply({ content, repliedAt, sellerLabel = "판매자 답글" }: { content: string; repliedAt?: string | null; sellerLabel?: string }) {
  return (
    <div className="ml-6 mt-3.5 max-w-[736px] rounded border-l-[3px] border-navy bg-surface px-4 py-3.5">
      <div className="mb-1 flex items-center gap-2 text-[13px] font-bold">
        <span className="rounded-sm bg-[#E8EDF7] px-1.5 py-0.5 text-xs text-navy">{sellerLabel}</span>
        {repliedAt && <span className="text-xs font-normal tabular-nums text-text-3">{formatDate(repliedAt)}</span>}
      </div>
      <p className="whitespace-pre-line leading-[1.7] text-text-2">{content}</p>
    </div>
  );
}
