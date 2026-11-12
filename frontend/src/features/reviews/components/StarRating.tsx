import { ratingFillPercent } from "@/lib/money";
import { cn } from "@/lib/utils";

const STAR = "#E8A317";

/** 표시 전용 별점(정수 별점, 채운 별 n개). */
export function StarRating({ rating, className }: { rating: number; className?: string }) {
  return (
    <span role="img" aria-label={`5점 만점에 ${rating}점`} className={cn("inline-flex text-sm leading-none tracking-[1px]", className)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} aria-hidden style={{ color: n <= rating ? STAR : undefined }} className={n <= rating ? "" : "text-border"}>
          ★
        </span>
      ))}
    </span>
  );
}

/** 평균 별점: 회색 별 위에 비율만큼 채운 별을 겹친다(너비는 ratingFillPercent). */
export function StarRatingAverage({ average }: { average: number }) {
  return (
    <span role="img" aria-label={`평균 별점 5점 만점에 ${average.toFixed(1)}점`} className="relative inline-block text-xl leading-none tracking-[2px] text-border">
      <span aria-hidden>★★★★★</span>
      <span aria-hidden className="absolute left-0 top-0 overflow-hidden whitespace-nowrap" style={{ width: `${ratingFillPercent(average)}%`, color: STAR }}>
        ★★★★★
      </span>
    </span>
  );
}
