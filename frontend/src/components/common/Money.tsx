import { formatKrw } from "@/lib/money";
import { cn } from "@/lib/utils";

export function Money({ value, className }: { value: number; className?: string }) {
  return <span className={cn("tabular-nums", className)}>{formatKrw(value)}</span>;
}
