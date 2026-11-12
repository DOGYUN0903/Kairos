import { cn } from "@/lib/utils";

/** 썸네일. url 이 없거나 비어 있으면 자리 표시 상자를 보인다. */
export function OrderThumb({ url, size, className }: { url: string | null | undefined; size: number; className?: string }) {
  const style = { width: size, height: size };
  if (!url) return <div aria-hidden style={style} className={cn("flex-none rounded bg-surface-2", className)} />;
  // eslint-disable-next-line @next/next/no-img-element -- 외부 이미지 URL 을 그대로 쓴다
  return <img src={url} alt="" style={style} className={cn("flex-none rounded bg-surface-2 object-cover", className)} />;
}
