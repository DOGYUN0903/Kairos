import { Logo } from "@/components/layout/StoreHeader";

export function Footer() {
  return (
    <footer className="mt-20 border-t border-border bg-surface py-10 text-[13px] text-text-2">
      <div className="container flex flex-col gap-3">
        <Logo size="sm" />
        <p>KAIROS 마켓플레이스</p>
      </div>
    </footer>
  );
}
